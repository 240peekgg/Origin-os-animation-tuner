package io.peek.vivomotion;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;
import rikka.shizuku.Shizuku;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
 private static final String KEY = "enter_exit_app_animation";
 private static final int REQUEST_SHIZUKU = 821;
 private final ExecutorService executor = Executors.newSingleThreadExecutor();
 private SharedPreferences prefs;
 private volatile IMotionShell service;
 private boolean binding;
 private TextView status, current, openingText, closingText, diag;
 private MotionPreview preview;
 private ChipGroup modes;
 private int[] chipIds = new int[4];
 private int chosen = 3, openingMs = 420, closingMs = 480;

 private final Shizuku.OnBinderReceivedListener listener = () -> runOnUiThread(this::checkShizuku);
 private final Shizuku.OnBinderDeadListener deadListener = () -> runOnUiThread(() -> {
  service = null; binding=false; showStatus("Shizuku отключён");
 });
 private final Shizuku.OnRequestPermissionResultListener permissionListener = (request, result) -> {
  if(request==REQUEST_SHIZUKU)runOnUiThread(this::checkShizuku);
 };
 private final ServiceConnection connection = new ServiceConnection() {
  @Override public void onServiceConnected(ComponentName name, IBinder binder) {
   service=IMotionShell.Stub.asInterface(binder);binding=false;showStatus("Подключено через Shizuku · shell");refreshMode();
  }
  @Override public void onServiceDisconnected(ComponentName name) {
   service=null;binding=false;showStatus("Соединение потеряно");
  }
 };

 @Override protected void onCreate(Bundle saved) {
  super.onCreate(saved);
  DynamicColors.applyToActivityIfAvailable(this);
  prefs=getSharedPreferences("motion_backup",MODE_PRIVATE);
  openingMs=prefs.getInt("preview_open",420);
  closingMs=prefs.getInt("preview_close",480);
  screen();
  Shizuku.addBinderReceivedListenerSticky(listener);
  Shizuku.addBinderDeadListener(deadListener);
  Shizuku.addRequestPermissionResultListener(permissionListener);
  checkShizuku();
 }
 private int dp(float x){return Math.round(getResources().getDisplayMetrics().density*x);}
 private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
 private LinearLayout.LayoutParams margin(int top){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;
 }
 private int color(int id,int fallback){return MaterialColors.getColor(this,id,fallback);}
 private TextView text(String value,int size,boolean bold){
  TextView v=new TextView(this);v.setText(value);v.setTextSize(size);
  v.setTextColor(color(com.google.android.material.R.attr.colorOnSurface,0xffeeeef3));
  if(bold)v.setTypeface(null,Typeface.BOLD);return v;
 }
 private MaterialButton button(String label,boolean outlined){
  MaterialButton b=outlined ? new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle) : new MaterialButton(this);
  b.setText(label);b.setAllCaps(false);b.setCornerRadius(dp(16));return b;
 }
 private LinearLayout section(LinearLayout root,String title,String description){
  View line=new View(this);
  line.setBackgroundColor(color(com.google.android.material.R.attr.colorOutlineVariant,0xff4a4b56));
  root.addView(line,margin(26));
  LinearLayout area=column();root.addView(area,margin(18));
  area.addView(text(title,20,true));
  TextView sub=text(description,13,false);
  sub.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant,0xffb4b4c2));
  area.addView(sub,margin(5));
  return area;
 }
 private void screen(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  LinearLayout root=column();root.setPadding(dp(20),dp(27),dp(20),dp(35));scroll.addView(root);setContentView(scroll);
  root.addView(text("Origin Motion",31,true));
  TextView subtitle=text("vivo  /  OriginOS 6",14,false);root.addView(subtitle,margin(5));

  LinearLayout access=section(root,"Подключение","Без root. Shizuku (ADB) или разрешение записи системных настроек");
  status=text("Проверяем доступ…",14,false);access.addView(status,margin(12));
  MaterialButton shizuku=button("Разрешить Shizuku",false);
  shizuku.setOnClickListener(v->requestShizuku());access.addView(shizuku,margin(10));
  MaterialButton local=button("Разрешить изменение настроек",true);
  local.setOnClickListener(v->{
   Intent i=new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
   i.setData(Uri.parse("package:"+getPackageName()));
   try{startActivity(i);}catch(Exception e){message(e.getMessage());}
  });access.addView(local,margin(6));

  LinearLayout sample=section(root,"Предпросмотр","Открытие и закрытие регулируются независимо в демонстрации");
  preview=new MotionPreview(this);
  sample.addView(preview,new LinearLayout.LayoutParams(-1,dp(270)));
  LinearLayout actions=new LinearLayout(this);
  actions.setOrientation(LinearLayout.HORIZONTAL);
  MaterialButton open=button("Открыть",false),close=button("Закрыть",true);
  actions.addView(open,new LinearLayout.LayoutParams(0,dp(50),1));
  LinearLayout.LayoutParams closeP=new LinearLayout.LayoutParams(0,dp(50),1);closeP.leftMargin=dp(10);
  actions.addView(close,closeP);sample.addView(actions,margin(12));
  open.setOnClickListener(v->preview.play(true,openingMs));
  close.setOnClickListener(v->preview.play(false,closingMs));
  openingText=text("",15,true);sample.addView(openingText,margin(18));
  Slider openSlider=makeSlider(openingMs);sample.addView(openSlider);
  openSlider.addOnChangeListener((s,val,fromUser)->{
   openingMs=Math.round(val);openingText.setText("Открытие · "+openingMs+" мс");
   if(fromUser){prefs.edit().putInt("preview_open",openingMs).apply();preview.play(true,openingMs);}
  });
  closingText=text("",15,true);sample.addView(closingText,margin(9));
  Slider closeSlider=makeSlider(closingMs);sample.addView(closeSlider);
  closeSlider.addOnChangeListener((s,val,fromUser)->{
   closingMs=Math.round(val);closingText.setText("Закрытие · "+closingMs+" мс");
   if(fromUser){prefs.edit().putInt("preview_close",closingMs).apply();preview.play(false,closingMs);}
  });
  openingText.setText("Открытие · "+openingMs+" мс");closingText.setText("Закрытие · "+closingMs+" мс");
  TextView note=text("Ползунки изменяют только предпросмотр. Произвольная независимая скорость системных переходов без root пока не подтверждена",12,false);
  note.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant,0xffb4b4c2));sample.addView(note,margin(12));

  LinearLayout system=section(root,"Системная анимация","Реальная настройка Vivo · enter_exit_app_animation");
  current=text("Считываем режим…",14,false);system.addView(current,margin(12));
  modes=new ChipGroup(this);modes.setSingleSelection(true);
  String[] names={"Нет","Быстрая","Умеренная","Комфортная"};
  for(int i=0;i<4;i++){
   Chip c=new Chip(this);c.setId(View.generateViewId());chipIds[i]=c.getId();
   c.setCheckable(true);c.setText(names[i]);modes.addView(c);
  }
  modes.check(chipIds[3]);
  modes.setOnCheckedStateChangeListener((g,ids)->{
   for(int i=0;i<4;i++) if(ids.contains(chipIds[i]))chosen=i;
  });
  system.addView(modes,margin(12));
  MaterialButton apply=button("Применить системный режим",false);
  apply.setOnClickListener(v->apply());system.addView(apply,margin(10));
  MaterialButton trial=button("Попробовать на 20 секунд",true);
  trial.setOnClickListener(v->{
   if(service==null){message("Пробный режим требует Shizuku");return;}
   task(sh->sh.trial(chosen,20),s->{current.setText(s);if(!s.startsWith("ERROR:"))current.postDelayed(this::refreshMode,21000);});
  });system.addView(trial,margin(7));
  MaterialButton confirm=button("Оставить пробный режим",true);
  confirm.setOnClickListener(v->task(IMotionShell::confirm,s->{current.setText(s);refreshMode();}));
  system.addView(confirm,margin(7));
  MaterialButton restore=button("Восстановить исходное",true);
  restore.setOnClickListener(v->restore());system.addView(restore,margin(7));
  TextView safety=text("Исходное значение сохраняется до первого изменения. При отказе в доступе приложение ничего не меняет",12,false);
  system.addView(safety,margin(9));

  LinearLayout diagnostics=section(root,"Диагностика","Только чтение, системные APK и свойства не изменяются");
  MaterialButton test=button("Проверить параметры",true);
  test.setOnClickListener(v->task(IMotionShell::diagnostics,s->diag.setText(s)));
  diagnostics.addView(test,margin(10));
  diag=text("Проверка ещё не запускалась",12,false);diag.setTextIsSelectable(true);diagnostics.addView(diag,margin(10));
 }
 private Slider makeSlider(int initial){
  Slider s=new Slider(this);s.setValueFrom(100);s.setValueTo(1200);s.setStepSize(10);
  int v=Math.max(100,Math.min(1200,initial));v=100+10*((v-100)/10);
  s.setValue(v);return s;
 }
 private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
 private void showStatus(String value){if(status!=null)status.setText(value);}
 private void requestShizuku(){
  try {
   if(!Shizuku.pingBinder()){message("Сначала запусти Shizuku");return;}
   if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(REQUEST_SHIZUKU);return;}
   bind();
  }catch(Throwable e){showStatus("Shizuku: "+e.getClass().getSimpleName());}
 }
 private void checkShizuku(){
  try {
   if(!Shizuku.pingBinder()){
    showStatus(Settings.System.canWrite(this)?"Доступ через WRITE_SETTINGS":"Shizuku выключен");
    if(Settings.System.canWrite(this))refreshMode();return;
   }
   if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){
    showStatus("Shizuku работает · нужно разрешение");
    if(Settings.System.canWrite(this))refreshMode();return;
   }
   if(service==null)bind();
   else{showStatus("Shizuku подключён");refreshMode();}
  }catch(Throwable e){showStatus("Проверь Shizuku / WRITE_SETTINGS");}
 }
 private void bind(){
  if(service!=null || binding)return;
  binding=true;
  try{
   Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(this,MotionShellService.class))
    .daemon(false).processNameSuffix("motion").debuggable(false).version(1);
   Shizuku.bindUserService(args,connection);
   showStatus("Подключение к Shizuku…");
  }catch(Throwable e){binding=false;showStatus("Ошибка подключения: "+e.getMessage());}
 }
 private interface Job {String call(IMotionShell shell) throws Exception;}
 private void task(Job job,java.util.function.Consumer<String> finished){
  IMotionShell backend=service!=null?service:(Settings.System.canWrite(this)?fallback():null);
  if(backend==null){message("Нужен Shizuku или WRITE_SETTINGS");return;}
  executor.execute(()->{
   String result;
   try{result=job.call(backend);}catch(Exception e){result="ERROR: "+e.getMessage();}
   String display=result;
   runOnUiThread(()->finished.accept(display));
  });
 }
 private IMotionShell fallback(){
  return new IMotionShell.Stub(){
   public String readMode(){
    try{String value=Settings.System.getString(getContentResolver(),KEY);return value==null?"null":value;}
    catch(Exception e){return "ERROR: "+e.getMessage();}
   }
   public String writeMode(int mode){
    if(mode<0||mode>3)return "ERROR: invalid mode";
    try{
     if(!Settings.System.canWrite(MainActivity.this)||!Settings.System.putInt(getContentResolver(),KEY,mode))return "ERROR: write denied";
     String value=readMode();return String.valueOf(mode).equals(value)?value:"ERROR: verify failed "+value;
    }catch(Exception e){return "ERROR: "+e.getMessage();}
   }
   public String restore(String original){
    if(original==null||!("null".equals(original)||original.matches("[0-9]{1,3}")))return "ERROR: invalid backup";
    try{
     if(!Settings.System.canWrite(MainActivity.this)||!Settings.System.putString(getContentResolver(),KEY,"null".equals(original)?null:original))return "ERROR: restore denied";
     String result=readMode();return original.equals(result)?result:"ERROR: verify failed "+result;
    }catch(Exception e){return "ERROR: "+e.getMessage();}
   }
   public String diagnostics(){return "backend=WRITE_SETTINGS\nmode="+readMode()+"\nno system properties changed";}
   public String trial(int mode,int seconds){return "ERROR: Shizuku required";}
   public String confirm(){return "ERROR: Shizuku required";}
  };
 }
 private boolean good(String value){return value!=null&&("null".equals(value)||value.matches("[0-9]{1,3}"));}
 private void refreshMode(){
  if(current==null)return;
  task(IMotionShell::readMode,result->{
   if(!good(result)){current.setText("Ошибка чтения: "+result);return;}
   current.setText("Текущий режим: "+result+(prefs.getBoolean("saved",false)?" · откат сохранён":""));
   try{int v=Integer.parseInt(result);if(v>=0&&v<=3)modes.check(chipIds[v]);}catch(Exception ignored){}
  });
 }
 private void apply(){
  int mode=chosen;
  task(shell->{
   String before=shell.readMode();
   if(!good(before))return "ERROR: cannot back up "+before;
   if(!prefs.getBoolean("saved",false)){
    if(!prefs.edit().putString("original",before).putBoolean("saved",true).commit())return "ERROR: backup failed";
   }
   return shell.writeMode(mode);
  },value->current.setText(value.startsWith("ERROR:")?value:"Применено и проверено · режим "+value));
 }
 private void restore(){
  if(!prefs.getBoolean("saved",false)){message("Нет сохранённого значения");return;}
  String before=prefs.getString("original","");
  if(!good(before)){current.setText("Ошибка резервной копии");return;}
  task(sh->sh.restore(before),result->{
   if(!result.startsWith("ERROR:")){prefs.edit().clear().commit();current.setText("Восстановлено: "+result);refreshMode();}
   else current.setText(result);
  });
 }
 @Override protected void onResume(){super.onResume();if(status!=null)checkShizuku();}
 @Override protected void onDestroy(){
  try{
   Shizuku.removeBinderReceivedListener(listener);
   Shizuku.removeBinderDeadListener(deadListener);
   Shizuku.removeRequestPermissionResultListener(permissionListener);
  }catch(Throwable ignored){}
  executor.shutdownNow();super.onDestroy();
 }
}
