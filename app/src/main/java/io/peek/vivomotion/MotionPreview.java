package io.peek.vivomotion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

public final class MotionPreview extends View {
 private final Paint p = new Paint(3);
 private final RectF r = new RectF();
 private ValueAnimator animator;
 private float progress = 1f;
 MotionPreview(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
 void play(boolean opening, int ms) {
  if (animator != null) animator.cancel();
  animator = ValueAnimator.ofFloat(opening ? 0 : 1, opening ? 1 : 0);
  animator.setDuration(Math.min(1200, Math.max(100, ms)));
  animator.setInterpolator(opening ? new PathInterpolator(.18f,.84f,.22f,1f) : new PathInterpolator(.4f,0f,1f,1f));
  animator.addUpdateListener(a -> {progress = (float)a.getAnimatedValue(); invalidate();});
  animator.start();
 }
 @Override protected void onDraw(Canvas c) {
  float d = getResources().getDisplayMetrics().density, w=getWidth(), h=getHeight();
  p.setColor(0xff14171e);c.drawRoundRect(0,0,w,h,24*d,24*d,p);
  float dock=h-28*d;
  for(int i=-1;i<=1;i++) {
   p.setColor(i==0?0xff859bd4:0xff626a7b);
   r.set(w/2+i*37*d-10*d,dock-10*d,w/2+i*37*d+10*d,dock+10*d);
   c.drawRoundRect(r,6*d,6*d,p);
  }
  float t=progress;
  float left = lerp(w/2-12*d,w*.2f,t), right=lerp(w/2+12*d,w*.8f,t);
  float top = lerp(dock-12*d,h*.09f,t), bottom=lerp(dock+12*d,h*.81f,t);
  p.setColor(0xffdbe3f9);r.set(left,top,right,bottom);
  c.drawRoundRect(r,lerp(7*d,19*d,t),lerp(7*d,19*d,t),p);
  p.setColor(0xff677fa8);
  if(t>.4f){c.drawRoundRect(left+18*d,top+31*d,right-26*d,top+38*d,4*d,4*d,p);}
 }
 private static float lerp(float a,float b,float t){return a+(b-a)*t;}
 @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
}
