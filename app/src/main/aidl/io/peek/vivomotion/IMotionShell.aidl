package io.peek.vivomotion;
interface IMotionShell {
 String readMode();
 String writeMode(int mode);
 String restore(String previous);
 String diagnostics();
 String inspectRootlessCapabilities();
 String trial(int mode, int seconds);
 String confirm();
}
