package com.aetherwild.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public final class GameOverlay extends View {
    private final Paint p = new Paint(3);
    private final GameRenderer game;
    private float w, h, density;
    private boolean started = false, paused = false;
    private int movePointer = -1, lookPointer = -1;
    private float moveX, moveY, lastLookX, lastLookY, stickX, stickY;
    private long lastUiTap;
    private String toast = "Find 5 Ether Shards. Activate the ancient beacon.";
    private long toastUntil = System.currentTimeMillis() + 6000;

    public GameOverlay(Context c, GameRenderer r) {
        super(c);
        game = r;
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private float d(float v) { return v * density; }
    private void paint(int color, Paint.Style style) { p.setColor(color); p.setStyle(style); p.setStrokeWidth(d(2)); }
    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        paint(color, Paint.Style.FILL); p.setTypeface(bold ? Typeface.create("sans-serif", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextSize(d(size)); c.drawText(s, x, y, p);
    }
    private void panel(Canvas c, float l, float t, float r, float b, int color, float rad) {
        paint(color, Paint.Style.FILL); c.drawRoundRect(l, t, r, b, d(rad), d(rad), p);
        paint(0x5549D9CD, Paint.Style.STROKE); c.drawRoundRect(l, t, r, b, d(rad), d(rad), p);
    }
    private void button(Canvas c, String label, float l, float t, float r, float b, boolean primary) {
        panel(c,l,t,r,b, primary ? 0xE52BAEA8 : 0xD9122938, 16);
        text(c,label,l+d(12),t+(b-t)*.63f,14,0xFFFFFFFF,true);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        w = getWidth(); h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (!started || paused) { drawMenu(canvas); return; }
        drawHud(canvas);
    }

    private void drawMenu(Canvas c) {
        paint(0xA8071421, Paint.Style.FILL); c.drawRect(0,0,w,h,p);
        float leftEnd=Math.min(w-d(340),d(500));
        panel(c,d(24),d(18),leftEnd,h-d(18),0xE50A1A28,22);
        text(c,"AETHERWILD",d(48),d(76),30,0xFFF1FFFF,true);
        text(c,"LOST HORIZON  /  MOBILE 3D SURVIVAL",d(50),d(102),11,0xFF70DCD2,true);
        text(c,"Explore a wild frontier. Recover ancient shards.",d(50),d(140),14,0xFFE0F2F7,false);
        text(c,"Move • Fight sentinels • Discover the beacon",d(50),d(163),13,0xFFB8D0DA,false);
        float bx = d(50), by = h-d(94);
        button(c, started ? "RESUME EXPEDITION" : "START EXPEDITION",bx,by,bx+d(220),by+d(48),true);
        text(c,"GRAPHICS PRESET",d(50),h-d(116),10,0xFF80A9B8,true);
        float sw = d(95), gap = d(7), sy = h-d(39);
        for (int i=0;i<3;i++) {
            float x=d(50)+i*(sw+gap);
            panel(c,x,sy,x+sw,sy+d(35),game.getGraphics()==i?0xE5379D96:0xB51A3040,10);
            text(c,(i+1)+". "+new String[]{"LOW","MEDIUM","EXTREME"}[i],x+d(8),sy+d(22),10,0xFFFFFFFF,true);
        }
        float right = w-d(24);
        float rightStart=Math.min(w-d(315),leftEnd+d(18));
        panel(c,rightStart,d(26),right,h-d(26),0xC90B1D2B,20);
        float rx=rightStart+d(18);
        text(c,"THE FRONTIER",rx,d(62),13,0xFFE6FFFF,true);
        text(c,"• Procedural island terrain",rx,d(88),12,0xFFD6E7EF,false);
        text(c,"• Dynamic day / night lighting",rx,d(112),12,0xFFD6E7EF,false);
        text(c,"• Ether shard collection quest",rx,d(136),12,0xFFD6E7EF,false);
        text(c,"• Sentinel combat + XP",rx,d(160),12,0xFFD6E7EF,false);
        text(c,"Made for GLES 2.0 phones",rx,h-d(50),11,0xFF64DCCF,true);
        text(c,"Current preset: "+new String[]{"LOW","MEDIUM","EXTREME"}[game.getGraphics()],rx,h-d(30),11,0xFFFFFFFF,true);
    }

    private void drawHud(Canvas c) {
        panel(c,d(14),d(12),d(250),d(76),0xB90A1A27,14);
        text(c,"AETHERWILD",d(26),d(35),13,0xFFEFFFFF,true);
        text(c,"HP  "+game.getHealth()+"    XP  "+game.getXp(),d(26),d(56),12,0xFFBEEBE7,true);
        text(c,"ETHER SHARDS  "+game.getShards()+"/5",d(26),d(70),10,0xFFFFD98B,true);
        panel(c,d(14),d(86),d(265),d(119),0xA90A1A27,11);
        text(c,game.isBeaconActive()?"QUEST COMPLETE — BEACON AWAKENED": "QUEST  •  Collect 5 shards, then find the beacon",d(25),d(106),10,0xFFE2F1F5,true);
        float mx=w-d(90), my=d(73);
        paint(0xC9162C3D, Paint.Style.FILL); c.drawCircle(mx,my,d(49),p);
        paint(0xFF58C9BB, Paint.Style.STROKE); c.drawCircle(mx,my,d(49),p);
        paint(0xFF4E9A90, Paint.Style.STROKE); c.drawLine(mx-d(31),my,mx+d(31),my,p); c.drawLine(mx,my-d(31),mx,my+d(31),p);
        paint(0xFFFFE3A2, Paint.Style.FILL); c.drawCircle(mx,my,d(4),p);
        text(c,new String[]{"LOW","MED","EXT"}[game.getGraphics()],mx-d(17),my+d(64),10,0xFFD9F6F4,true);
        button(c,"Ⅱ",w-d(61),d(126),w-d(14),d(164),false);

        float sx=d(87), sy=h-d(83);
        paint(0x6038D8CA, Paint.Style.FILL); c.drawCircle(sx,sy,d(57),p);
        paint(0xB9C8F5F0, Paint.Style.STROKE); c.drawCircle(sx,sy,d(57),p);
        paint(0xD5DDFBF7, Paint.Style.FILL); c.drawCircle(sx+moveX*d(36),sy+moveY*d(36),d(22),p);
        text(c,"MOVE",sx-d(18),sy+d(77),10,0xFFDDF6F3,true);

        float base=w-d(180), y=h-d(84);
        actionCircle(c,base+d(82),y,d(33),"HIT",0xFFF27869);
        actionCircle(c,base,y+d(2),d(29),"JUMP",0xFFE1BE77);
        actionCircle(c,base+d(164),y+d(2),d(29),"USE",0xFF65D6CB);
        text(c,"Drag right side to look around",w-d(265),h-d(20),10,0xA9FFFFFF,false);
        if (System.currentTimeMillis()<toastUntil) {
            panel(c,w*.32f,h-d(112),w*.68f,h-d(74),0xD90B202D,12);
            text(c,toast,w*.34f,h-d(88),11,0xFFFFFFFF,true);
        }
        if (game.getHealth()<=0) {
            panel(c,w*.34f,h*.36f,w*.66f,h*.62f,0xED081421,20);
            text(c,"YOU FELL",w*.42f,h*.44f,24,0xFFFFA68B,true);
            button(c,"RESPAWN",w*.42f,h*.49f,w*.58f,h*.57f,true);
        }
    }

    private void actionCircle(Canvas c,float x,float y,float radius,String label,int color) {
        paint(0xA5172938,Paint.Style.FILL); c.drawCircle(x,y,radius,p);
        paint(color,Paint.Style.STROKE); p.setStrokeWidth(d(3)); c.drawCircle(x,y,radius,p);
        text(c,label,x-d(label.length()*3.5f),y+d(4),10,0xFFFFFFFF,true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(), y=e.getY();
        int action=e.getActionMasked();
        int id=e.getPointerId(e.getActionIndex());
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            if (!started || paused) {
                if (y>h-d(98) && y<h-d(42) && x>=d(40) && x<d(310)) {
                    if (paused) { paused=false; started=true; }
                    else { started=true; paused=false; game.begin(); }
                } else if (y>h-d(42) && x>=d(45) && x<d(360)) {
                    int index=(int)((x-d(50))/(d(95)+d(7)));
                    if(index>=0 && index<3) game.setGraphics(index);
                } else if (started && paused && x<w-d(24) && y>d(126)) {
                    paused=false; started=true;
                }
                invalidate(); return true;
            }
            if (game.getHealth()<=0) { game.reset(); invalidate(); return true; }
            if (x>w-d(70) && y<d(180)) { paused=true; game.setMove(0,0); invalidate(); return true; }
            if (y>h-d(145) && x>w-d(135) && x<w-d(58)) {
                game.attack(); toast=game.getLastMessage(); toastUntil=System.currentTimeMillis()+2200; invalidate(); return true;
            }
            if (y>h-d(145) && x>w-d(58)) {
                game.interact(); toast=game.getLastMessage(); toastUntil=System.currentTimeMillis()+2600; invalidate(); return true;
            }
            if (y>h-d(145) && x>w-d(220) && x<w-d(140)) { game.jump(); return true; }
            if (x<w*.38f && y>h*.47f && movePointer==-1) {
                movePointer=id; updateMove(x,y); return true;
            }
            if (lookPointer==-1) { lookPointer=id; lastLookX=x; lastLookY=y; return true; }
            return true;
        }
        if (action==MotionEvent.ACTION_MOVE) {
            for (int i=0;i<e.getPointerCount();i++) {
                int pid=e.getPointerId(i);
                float px=e.getX(i), py=e.getY(i);
                if (pid==movePointer) updateMove(px,py);
                if (pid==lookPointer) {
                    float dx=px-lastLookX, dy=py-lastLookY;
                    lastLookX=px; lastLookY=py;
                    game.look(dx/density,dy/density);
                }
            }
            invalidate(); return true;
        }
        if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_CANCEL) {
            if (id==movePointer || action==MotionEvent.ACTION_CANCEL) { movePointer=-1; moveX=moveY=0; game.setMove(0,0); }
            if (id==lookPointer || action==MotionEvent.ACTION_CANCEL) lookPointer=-1;
            invalidate(); return true;
        }
        return true;
    }

    private void updateMove(float x,float y) {
        float cx=d(87), cy=h-d(83), max=d(49);
        moveX=Math.max(-1,Math.min(1,(x-cx)/max));
        moveY=Math.max(-1,Math.min(1,(y-cy)/max));
        stickX=moveX; stickY=moveY; game.setMove(moveX,moveY); invalidate();
    }
}
