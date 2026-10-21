package io.peek.vivomotion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService, runs under shell UID; no system property modification. */
public final class MotionShellService extends IMotionShell.Stub {
 private static final String KEY = "enter_exit_app_animation";
 private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
 private ScheduledFuture<?> pending;
 private String beforeTrial;
 public MotionShellService() { }

 private String cmd(String... args) {
  Process p = null;
  try {
   p = new ProcessBuilder(args).redirectErrorStream(true).start();
   if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroyForcibly(); return "ERROR: timeout"; }
   StringBuilder out = new StringBuilder();
   try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
    String line;
    while ((line = br.readLine()) != null && out.length() < 2048) out.append(line).append('\n');
   }
   return p.exitValue() == 0 ? out.toString().trim() : "ERROR: exit=" + p.exitValue() + " " + out;
  } catch (Exception e) {
   if (p != null) p.destroyForcibly();
   return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
  }
 }
 public String readMode() { return cmd("/system/bin/settings","get","system",KEY); }
 public synchronized String writeMode(int mode) {
  if (mode < 0 || mode > 3) return "ERROR: invalid mode";
  String result = cmd("/system/bin/settings","put","system",KEY,String.valueOf(mode));
  if (result.startsWith("ERROR:")) return result;
  String value = readMode();
  return value.equals(String.valueOf(mode)) ? value : "ERROR: read-back mismatch: " + value;
 }
 public synchronized String restore(String original) {
  if (original == null || !("null".equals(original) || original.matches("[0-9]{1,3}"))) return "ERROR: invalid backup";
  String result = "null".equals(original)
    ? cmd("/system/bin/settings","delete","system",KEY)
    : cmd("/system/bin/settings","put","system",KEY,original);
  if (result.startsWith("ERROR:")) return result;
  String actual = readMode();
  return original.equals(actual) ? actual : "ERROR: restore mismatch: " + actual;
 }
 public synchronized String trial(int mode,int seconds) {
  if (mode < 0 || mode > 3 || seconds < 10 || seconds > 60) return "ERROR: invalid trial";
  if (pending != null) return "ERROR: trial already active";
  String original = readMode();
  if (!("null".equals(original) || original.matches("[0-9]{1,3}"))) return "ERROR: cannot back up";
  String changed = writeMode(mode);
  if (changed.startsWith("ERROR:")) return changed;
  beforeTrial = original;
  pending = scheduler.schedule(() -> {
   synchronized (MotionShellService.this) {
    if (beforeTrial != null) restore(beforeTrial);
    beforeTrial = null; pending = null;
   }
  }, seconds, TimeUnit.SECONDS);
  return "TRIAL: " + changed;
 }
 public synchronized String confirm() {
  if (pending == null) return "ERROR: no active trial";
  pending.cancel(false); pending = null; beforeTrial = null;
  return "CONFIRMED: " + readMode();
 }

 public String inspectRootlessCapabilities() {
  StringBuilder report = new StringBuilder("Rootless diagnostics\n");
  report.append("uid/context: ").append(cmd("/system/bin/id")).append("\n");
  report.append("Security patch: ").append(cmd("/system/bin/getprop","ro.build.version.security_patch")).append("\n");
  String[] keys = {
   "persist.is_debug_params",
   "open.persist.rect.centerx.stiffness",
   "open.persist.rect.centerx.dampingradio",
   "close.persist.rect.nrt_winBlur.stiffnes",
   "multiple.close.persist.rect.CENTER.X.stiffness"
  };
  for (String key : keys) {
   report.append("\n").append(key).append("\nvalue=")
     .append(cmd("/system/bin/getprop",key))
     .append("\nSELinux=")
     .append(cmd("/system/bin/getprop","-Z",key)).append("\n");
  }
  final String testKey = "debug.originmotion.probe";
  String probeContext = cmd("/system/bin/getprop","-Z",testKey);
  String targetContext = cmd("/system/bin/getprop","-Z","persist.is_debug_params");
  report.append("\nProbe context: ").append(probeContext);
  report.append("\nTarget context: ").append(targetContext);
  if (!probeContext.equals(targetContext)) report.append("\nWarning: debug-property permission is NOT proof of target property permission");
  String original = cmd("/system/bin/getprop",testKey);
  if (original.isEmpty()) {
   String write = cmd("/system/bin/setprop",testKey,"1");
   String after = cmd("/system/bin/getprop",testKey);
   boolean allowed = !write.startsWith("ERROR:") && "1".equals(after);
   report.append("\nTemporary default_prop write: ").append(allowed ? "ALLOWED" : "BLOCKED");
   if (!allowed) report.append("\n").append(write);
   if ("1".equals(after)) {
    String cleared = cmd("/system/bin/setprop",testKey,"");
    report.append("\nProbe cleanup: ").append(cleared.startsWith("ERROR:") ? cleared : "OK");
   }
  } else {
   report.append("\nProbe skipped: temporary property already exists");
  }
  report.append("\nNo launcher settings or properties changed");
  return report.toString();
 }
 public String diagnostics() {
  return "uid=" + android.os.Process.myUid() + "\n"
   + "mode=" + readMode() + "\n"
   + "patch=" + cmd("/system/bin/getprop","ro.build.version.security_patch") + "\n"
   + "debug_flag=" + cmd("/system/bin/getprop","persist.is_debug_params") + "\n"
   + "open_stiffness=" + cmd("/system/bin/getprop","open.persist.rect.centerx.stiffness") + "\n"
   + "close_stiffness=" + cmd("/system/bin/getprop","close.persist.rect.nrt_winBlur.stiffnes")
   + "\nNo system properties were changed";
 }
}
