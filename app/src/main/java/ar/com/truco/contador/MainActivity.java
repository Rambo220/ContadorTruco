package ar.com.truco.contador;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final int[] scores = {0,0};
    private final String[] names = {"Nosotros","Ellos"};
    private final ArrayList<int[]> history = new ArrayList<>();
    private int goal = 30;
    private Board board;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(43,26,17));
        getWindow().setNavigationBarColor(Color.rgb(43,26,17));
        prefs = getSharedPreferences("partida", MODE_PRIVATE);
        load();
        board = new Board();
        setContentView(board);
    }

    private void load() {
        goal = prefs.getInt("goal", 30);
        if (goal != 15 && goal != 30) goal = 30;
        for (int i = 0; i < 2; i++) {
            scores[i] = Math.max(0, Math.min(goal, prefs.getInt("s"+i, 0)));
            String storedName = prefs.getString("n"+i, i==0 ? "Nosotros" : "Ellos");
            if (storedName == null) storedName = i==0 ? "Nosotros" : "Ellos";
            storedName = storedName.trim();
            names[i] = storedName.isEmpty() ? (i==0 ? "Nosotros" : "Ellos") :
                    storedName.substring(0, Math.min(14, storedName.length()));
        }
        String saved = prefs.getString("history", "");
        for (String item : saved.split(";")) {
            try {
                String[] x = item.split(",");
                if (x.length == 3) {
                    int team = Integer.parseInt(x[0]);
                    int before = Integer.parseInt(x[1]);
                    int after = Integer.parseInt(x[2]);
                    if ((team == 0 || team == 1) && before >= 0 && after >= 0 && before <= goal && after <= goal)
                        history.add(new int[]{team,before,after});
                }
            } catch (NumberFormatException ignored) {}
        }
        while (history.size() > 100) history.remove(0);
    }

    private void save() {
        StringBuilder h = new StringBuilder();
        for (int[] x : history)
            h.append(x[0]).append(',').append(x[1]).append(',').append(x[2]).append(';');
        prefs.edit()
            .putInt("goal", goal)
            .putInt("s0", scores[0]).putInt("s1", scores[1])
            .putString("n0", names[0]).putString("n1", names[1])
            .putString("history", h.toString()).apply();
    }

    private void add(int team, int amount) {
        if ((scores[0] >= goal || scores[1] >= goal) && amount > 0) return;
        int before = scores[team], after = Math.max(0, Math.min(goal, before + amount));
        if (before == after) return;
        scores[team] = after;
        history.add(new int[]{team,before,after});
        if (history.size() > 100) history.remove(0);
        save();
        board.invalidate();
        if (after >= goal) {
            new AlertDialog.Builder(this)
                .setTitle("¡" + names[team] + " ganaron!")
                .setMessage("Llegaron a " + goal + " puntos. Podés deshacer o empezar otra partida.")
                .setPositiveButton("Cerrar", null)
                .setNegativeButton("Revancha", (d,w) -> reset())
                .show();
        }
    }

    private void undo() {
        if (history.isEmpty()) return;
        int[] last = history.remove(history.size()-1);
        scores[last[0]] = last[1];
        save();
        board.invalidate();
    }

    private void reset() {
        scores[0] = 0;
        scores[1] = 0;
        history.clear();
        save();
        board.historyOpen = false;
        board.invalidate();
    }

    private void rename(int team) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(names[team]);
        new AlertDialog.Builder(this)
            .setTitle("Nombre del equipo")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d,w) -> {
                String value = input.getText().toString().trim();
                if (!value.isEmpty()) {
                    names[team] = value.length() > 14 ? value.substring(0,14) : value;
                    save();
                    board.invalidate();
                }
            }).show();
    }

    private void settings() {
        new AlertDialog.Builder(this)
            .setTitle("Puntos de la partida")
            .setSingleChoiceItems(new String[]{"15 puntos","30 puntos"}, goal == 15 ? 0 : 1, (d,which) -> {
                int next = which == 0 ? 15 : 30;
                d.dismiss();
                if (goal == next) return;
                new AlertDialog.Builder(this)
                    .setTitle("Cambiar modalidad")
                    .setMessage("Cambiar los puntos reinicia la partida actual.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Cambiar", (a,b) -> { goal = next; reset(); })
                    .show();
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private final class Board extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.truco_logo);
        final List<Hit> hits = new ArrayList<>();
        boolean historyOpen = false;
        float scale, offsetY, offsetX;
        final int wood = Color.rgb(62,38,24), felt = Color.rgb(22,57,44);
        final int paper = Color.rgb(229,207,169), ink = Color.rgb(35,25,18);
        final int cream = Color.rgb(248,234,209), gold = Color.rgb(210,175,120);
        final int blue = Color.rgb(24,94,141), red = Color.rgb(158,44,34);

        int systemTop = 0, systemBottom = 0;
        Board() {
            super(MainActivity.this);
            setOnApplyWindowInsetsListener((v, insets) -> {
                systemTop = insets.getSystemWindowInsetTop();
                systemBottom = insets.getSystemWindowInsetBottom();
                invalidate();
                return insets;
            });
        }

        void rect(Canvas c,float x,float y,float w,float h,float r,int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(x,y,x+w,y+h),r,r,p);
        }
        void stroke(Canvas c,float x,float y,float w,float h,float r,int color,float sw) {
            p.setColor(color); p.setStrokeWidth(sw); p.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(new RectF(x,y,x+w,y+h),r,r,p);
            p.setStyle(Paint.Style.FILL);
        }
        void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,boolean center) {
            p.setColor(color);
            p.setTypeface(android.graphics.Typeface.create("serif",bold ? 1 : 0));
            p.setTextSize(size); p.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
            c.drawText(s,x,y,p);
        }
        void line(Canvas c,float x,float y,float xx,float yy,int color,float thick) {
            p.setColor(color); p.setStrokeWidth(thick); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x,y,xx,yy,p);
        }
        void button(Canvas c,float x,float y,float w,float h,String label,int color,Runnable action) {
            rect(c,x,y+3,w,h,9,Color.rgb(31,21,17));
            rect(c,x,y,w,h,9,color);
            stroke(c,x+1,y+1,w-2,h-2,9,gold,1);
            text(c,label,x+w/2,y+h*.67f, (label.startsWith("+") || label.equals("−1")) ? h*.55f : h*.46f,cream,true,true);
            hits.add(new Hit(x,y,w,h,action));
        }
        void five(Canvas c,float x,float y,float sz,int n,int active) {
            rect(c,x,y,sz,sz,5,Color.rgb(29,58,44));
            stroke(c,x,y,sz,sz,5,Color.rgb(166,138,101),1);
            float a=sz*.25f, b=sz*.75f;
            float top=y+sz*.23f, bottom=y+sz*.77f;
            float left=x+a,right=x+b;
            float[][] lines = {
                {left,top,right,top},{right,top,right,bottom},
                {right,bottom,left,bottom},{left,bottom,left,top},
                {left,bottom,right,top}
            };
            for (int i=0; i<5; i++) {
                int color = i < n ? active : Color.rgb(49,79,62);
                line(c,lines[i][0],lines[i][1],lines[i][2],lines[i][3],
                     color,Math.max(2.6f,sz*.075f));
            }
        }
        void group(Canvas c,float x,float y,float width,int number,int color) {
            float gap=3, side=(width-gap*2)/3;
            for(int i=0;i<3;i++)
                five(c,x+i*(side+gap),y,side,Math.max(0,Math.min(5,number-i*5)),color);
        }
        void team(Canvas c,int t,float x) {
            int color=t==0?blue:red;
            rect(c,x,130,170,446,13,paper);
            stroke(c,x,130,170,446,13,Color.rgb(152,115,74),2);
            rect(c,x+8,141,154,36,5,color);
            text(c,names[t],x+84,165,21,cream,true,true);
            hits.add(new Hit(x+8,141,154,36,()->rename(t)));
            text(c,""+scores[t],x+85,255,68,ink,true,true);
            text(c,"/"+goal,x+85,280,17,Color.rgb(100,76,53),true,true);
            int bad=Math.min(15,scores[t]);
            int good=goal==30?Math.max(0,scores[t]-15):0;
            text(c,"BUENAS",x+12,302,14,ink,true,false);
            text(c,good+"/15",x+141,302,13,ink,true,true);
            group(c,x+9,311,152,good,color);
            text(c,"MALAS",x+12,382,14,ink,true,false);
            text(c,bad+"/15",x+141,382,13,ink,true,true);
            group(c,x+9,391,152,bad,color);
            float bx=x+8;
            for(int i=0;i<4;i++) {
                int n=i+1;
                button(c,bx+(i%2)*79,448+(i/2)*43,75,39,"+"+n,color,()->add(t,n));
            }
            button(c,bx,534,154,36,"−1",color,()->add(t,-1));
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float safeHeight = Math.max(1f, getHeight()-systemTop-systemBottom);
            scale=Math.min(getWidth()/380f,safeHeight/780f);
            offsetY=systemTop + Math.max(0,(safeHeight-780*scale)/2);
            offsetX=(getWidth()-380*scale)/2;
            c.drawColor(wood);
            c.save(); c.translate(offsetX,offsetY); c.scale(scale,scale);
            hits.clear();
            rect(c,10,58,360,684,22,felt);
            stroke(c,10,58,360,684,22,Color.rgb(109,79,48),5);
            if (logo != null) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                p.setAlpha(255);
                p.setFilterBitmap(true);
                c.drawBitmap(logo, null, new RectF(120,5,161,46), p);
            }
            text(c,"TRUCO",224,40,29,cream,true,true);
            text(c,"PARTIDA A "+goal+" PUNTOS",190,95,13,gold,true,true);
            button(c,18,12,45,35,"≡",Color.rgb(99,66,41),()->settings());
            button(c,319,12,43,35,"⚙",Color.rgb(99,66,41),()->settings());
            team(c,0,17); team(c,1,193);
            button(c,18,579,137,48,"Deshacer",Color.rgb(34,55,66),()->undo());
            button(c,170,579,38,48,"↶",Color.rgb(109,69,35),()->undo());
            button(c,224,579,138,48,"Nueva",Color.rgb(33,103,63),()->
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Nueva partida")
                    .setMessage("¿Reiniciar los puntos y el historial?")
                    .setNegativeButton("Cancelar",null)
                    .setPositiveButton("Reiniciar",(d,w)->reset()).show());
            rect(c,26,644,328,57,10,Color.rgb(31,23,18));
            stroke(c,26,644,328,57,10,Color.rgb(113,82,51),1);
            text(c,"ÚLTIMOS PUNTOS",44,668,19,cream,true,false);
            text(c,history.size()+" mov.",302,687,12,gold,false,true);
            text(c,"⌄",336,674,23,cream,true,true);
            hits.add(new Hit(26,644,328,57,()->{ historyOpen=true; invalidate(); }));
            text(c,"Sin internet ni servicios de fondo",190,724,12,gold,false,true);

            if(historyOpen) {
                rect(c,0,0,380,780,0,0xB9000000);
                rect(c,18,250,344,470,15,Color.rgb(28,21,18));
                stroke(c,18,250,344,470,15,gold,1);
                text(c,"Últimos puntos",35,286,27,cream,true,false);
                rect(c,306,259,42,35,9,Color.rgb(90,57,38));
                text(c,"×",327,284,25,cream,true,true);
                if(history.isEmpty()) text(c,"No hay movimientos",190,360,19,cream,false,true);
                else for(int i=history.size()-1,j=0;i>=0&&j<9;i--,j++) {
                    int[] m=history.get(i);
                    int yy=336+j*39;
                    text(c,(m[2]-m[1]>0?"+":"")+(m[2]-m[1]),36,yy,23,
                         m[0]==0?0xff78b6e3:0xffff8278,true,false);
                    text(c,names[m[0]],98,yy,18,cream,true,false);
                    text(c,"→ "+m[2],314,yy,18,gold,false,true);
                    line(c,32,yy+11,346,yy+11,0xff46352b,1);
                }
            }
            c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=(e.getX()-offsetX)/scale,y=(e.getY()-offsetY)/scale;
            if(historyOpen) {
                if ((x>=306 && x<=348 && y>=259 && y<=294)
                    || x<18 || x>362 || y<250 || y>720) {
                    historyOpen=false;
                    invalidate();
                }
                return true; // El panel modal bloquea todos los botones de fondo.
            }
            for(int i=hits.size()-1;i>=0;i--) {
                Hit h=hits.get(i);
                if(x>=h.x && x<=h.x+h.w && y>=h.y && y<=h.y+h.h) {
                    h.action.run();
                    return true;
                }
            }
            return true;
        }
        final class Hit {
            final float x,y,w,h;
            final Runnable action;
            Hit(float x,float y,float w,float h,Runnable action) {
                this.x=x;this.y=y;this.w=w;this.h=h;this.action=action;
            }
        }
    }
}
