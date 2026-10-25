package com.aetherwild.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    private final SharedPreferences prefs;
    private volatile int graphics = 1;
    private volatile float moveX, moveY;
    private volatile float lookYaw, lookPitch;
    private volatile boolean attackQueued, jumpQueued, interactQueued, resetQueued;
    private volatile boolean playing;
    private int appliedGraphics = -1;
    private int program, aPosition, aNormal, aColor, uVP, uModel, uLight, uFog, uEye, uTime, uMaterial, uQuality;
    private final float[] projection = new float[16], view = new float[16], vp = new float[16], model = new float[16];
    private int width=1, height=1;
    private long lastNanos=0;
    private float time=0, day=0.6f, playerX=0, playerZ=12, playerY=2, verticalVelocity=0, cameraYaw=0, cameraPitch=0.26f;
    private int health=100, xp=0, shards=0, aliveSentinels=8;
    private boolean beaconActive=false, jumping=false;
    private String lastMessage="Find 5 Ether Shards.";
    private Mesh terrain, forest, props, water, cube, sphere, crystal, beacon;
    private final float[] shardX={12,-10,20,-24,10,32,-33,4};
    private final float[] shardZ={5,-8,20,17,-30,-12,-9,38};
    private final boolean[] shardTaken=new boolean[8];
    private final float[] enemyX=new float[12], enemyZ=new float[12];
    private final int[] enemyHp=new int[12];
    private final float[] light={-0.35f,0.86f,0.42f};
    private float daylight=0.8f;

    GameRenderer(Context c) {
        context=c;
        prefs=c.getSharedPreferences("aetherwild",Context.MODE_PRIVATE);
        graphics=prefs.getInt("graphics",0);
    }
    public int getGraphics(){return graphics;}
    public int getHealth(){return health;}
    public int getXp(){return xp;}
    public int getShards(){return shards;}
    public boolean isBeaconActive(){return beaconActive;}
    public String getLastMessage(){return lastMessage;}
    public void setGraphics(int q){graphics=Math.max(0,Math.min(2,q));prefs.edit().putInt("graphics",graphics).apply();}
    public void begin(){playing=true;}
    public void setMove(float x,float y){moveX=x;moveY=y;}
    public void look(float dx,float dy){cameraYaw+=dx*0.008f;cameraPitch=Math.max(0.08f,Math.min(0.8f,cameraPitch+dy*0.004f));}
    public void jump(){jumpQueued=true;}
    public void attack(){attackQueued=true;}
    public void interact(){interactQueued=true;}
    public void reset(){resetQueued=true;playing=true;}

    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        try {
            program=createProgram(VERTEX,FRAGMENT);
        } catch (RuntimeException shaderError) {
            android.util.Log.e("Aetherwild", "Primary shader failed; switching to GLES2 fallback", shaderError);
            program=createProgram(FALLBACK_VERTEX,FALLBACK_FRAGMENT);
        }
        aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        aNormal=GLES20.glGetAttribLocation(program,"aNormal");
        aColor=GLES20.glGetAttribLocation(program,"aColor");
        uVP=GLES20.glGetUniformLocation(program,"uVP");
        uModel=GLES20.glGetUniformLocation(program,"uModel");
        uLight=GLES20.glGetUniformLocation(program,"uLight");
        uFog=GLES20.glGetUniformLocation(program,"uFog");
        uEye=GLES20.glGetUniformLocation(program,"uEye");
        uTime=GLES20.glGetUniformLocation(program,"uTime");
        uMaterial=GLES20.glGetUniformLocation(program,"uMaterial");
        uQuality=GLES20.glGetUniformLocation(program,"uQuality");
        cube=makeCube(); sphere=makeSphere(10,14); crystal=makeCrystal();
        rebuildWorld();
    }

    @Override public void onSurfaceChanged(GL10 gl,int w,int h) {
        width=Math.max(1,w); height=Math.max(1,h);
        GLES20.glViewport(0,0,width,height);
        Matrix.perspectiveM(projection,0,57f,(float)width/height,0.1f,260f);
    }

    @Override public void onDrawFrame(GL10 gl) {
        long now=System.nanoTime();
        float dt=lastNanos==0?0.016f:Math.min(0.05f,(now-lastNanos)/1.0e9f);
        lastNanos=now;
        if (appliedGraphics!=graphics) rebuildWorld();
        time+=dt;
        daylight=0.5f+0.5f*(float)Math.sin(time*0.018f+0.8f);
        light[0]=-0.35f+0.55f*(float)Math.sin(time*0.018f); light[1]=0.30f+0.60f*daylight; light[2]=0.42f;
        if (resetQueued) { resetQueued=false; playerX=0;playerZ=12;verticalVelocity=0;health=100;xp=0;shards=0;aliveSentinels=8;beaconActive=false;for(int i=0;i<shardTaken.length;i++)shardTaken[i]=false;for(int i=0;i<enemyHp.length;i++)enemyHp[i]=i<8?2:0;lastMessage="Expedition restarted."; }
        if (playing) update(dt);
        float skyR=0.035f+daylight*0.20f, skyG=0.08f+daylight*0.36f, skyB=0.14f+daylight*0.47f;
        GLES20.glClearColor(skyR,skyG,skyB,1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        float targetX=playerX, targetY=playerY+1.4f, targetZ=playerZ;
        float dist=graphics==0?7.8f:graphics==1?7.0f:6.5f;
        float camX=targetX-(float)Math.sin(cameraYaw)*dist;
        float camZ=targetZ-(float)Math.cos(cameraYaw)*dist;
        float camY=targetY+dist*(float)Math.sin(cameraPitch);
        Matrix.setLookAtM(view,0,camX,camY,camZ,targetX,targetY,targetZ,0,1,0);
        Matrix.multiplyMM(vp,0,projection,0,view,0);
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uVP,1,false,vp,0);
        GLES20.glUniform3f(uLight,light[0],light[1],light[2]);
        GLES20.glUniform3f(uFog,skyR,skyG,skyB);
        GLES20.glUniform3f(uEye,camX,camY,camZ);
        GLES20.glUniform1f(uTime,time);
        GLES20.glUniform1f(uQuality,graphics);
        draw(terrain,identity(),0);
        draw(water,identity(),1);
        draw(forest,identity(),0);
        draw(props,identity(),0);
        drawBeacon();
        drawShards();
        drawSentinels();
        drawPlayer();
    }

    private void update(float dt) {
        float forward=-moveY, sideways=moveX;
        float dx=(sideways*(float)Math.cos(cameraYaw)+forward*(float)Math.sin(cameraYaw));
        float dz=(forward*(float)Math.cos(cameraYaw)-sideways*(float)Math.sin(cameraYaw));
        float len=(float)Math.sqrt(dx*dx+dz*dz);
        if(len>1){dx/=len;dz/=len;}
        float speed=graphics==0?4.2f:5.0f;
        playerX+=dx*speed*dt; playerZ+=dz*speed*dt;
        float radius=(float)Math.sqrt(playerX*playerX+playerZ*playerZ);
        if(radius>63){playerX*=63/radius;playerZ*=63/radius;}
        float ground=height(playerX,playerZ)+0.15f;
        if(jumpQueued && !jumping){verticalVelocity=7.1f;jumping=true;lastMessage="Jump!";} jumpQueued=false;
        verticalVelocity-=18f*dt; playerY+=verticalVelocity*dt;
        if(playerY<ground){playerY=ground;verticalVelocity=0;jumping=false;}
        if(attackQueued){attackQueued=false;int target=-1;float best=7f;for(int i=0;i<8;i++)if(enemyHp[i]>0){float dist=distance(playerX,playerZ,enemyX[i],enemyZ[i]);if(dist<best){best=dist;target=i;}}if(target>=0){enemyHp[target]--;if(enemyHp[target]<=0){aliveSentinels--;xp+=25;lastMessage="Sentinel defeated! +25 XP";}else lastMessage="Sentinel hit! One more strike."; }else lastMessage="No sentinel in range. Move closer.";}
        if(interactQueued){interactQueued=false;int found=-1;float best=3.3f;for(int i=0;i<shardX.length;i++)if(!shardTaken[i]){float dist=distance(playerX,playerZ,shardX[i],shardZ[i]);if(dist<best){best=dist;found=i;}}if(found>=0){shardTaken[found]=true;shards++;xp+=10;lastMessage="Ether shard acquired! +10 XP";}else if(distance(playerX,playerZ,0,-35)<5){if(shards>=5){beaconActive=true;xp+=100;lastMessage="THE BEACON AWAKENS! +100 XP";}else lastMessage="Beacon locked. Need "+(5-shards)+" more shard(s).";}else{lastMessage="Nothing to use nearby. Seek glowing shards.";}}
        for(int i=0;i<8;i++) if(enemyHp[i]>0 && distance(playerX,playerZ,enemyX[i],enemyZ[i])<2.0f && Math.random()<dt*0.12){health=Math.max(0,health-3);}
    }

    private void rebuildWorld() {
        appliedGraphics=graphics;
        int grid=graphics==0?48:graphics==1?72:100;
        terrain=makeTerrain(grid);
        forest=makeForest(graphics==0?58:graphics==1?105:165);
        props=makeProps();
        water=makeWater();
        beacon=makeBeacon();
        Random r=new Random(8031);
        for(int i=0;i<enemyX.length;i++){double a=r.nextDouble()*Math.PI*2;float rad=12+r.nextFloat()*34;enemyX[i]=(float)Math.cos(a)*rad;enemyZ[i]=(float)Math.sin(a)*rad;enemyHp[i]=i<8?2:0;}
    }

    private Mesh makeTerrain(int n) {
        Builder b=new Builder(n*n*6);
        float span=140f, step=span/n;
        for(int iz=0;iz<n;iz++)for(int ix=0;ix<n;ix++){
            float x0=-span/2+ix*step,z0=-span/2+iz*step,x1=x0+step,z1=z0+step;
            float[] a=terrainVertex(x0,z0),c=terrainVertex(x1,z0),d=terrainVertex(x0,z1),e=terrainVertex(x1,z1);
            b.tri(a,d,c);b.tri(c,d,e);
        }
        return b.finish();
    }
    private float[] terrainVertex(float x,float z){
        float y=height(x,z), e=0.25f;
        float nx=height(x-e,z)-height(x+e,z), ny=2*e, nz=height(x,z-e)-height(x,z+e);
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=len;ny/=len;nz/=len;
        float r,g,b;
        if(y>16){r=.82f;g=.88f;b=.91f;}
        else if(y>9){r=.38f;g=.42f;b=.39f;}
        else if(y>3.8f){r=.31f;g=.39f;b=.25f;}
        else if(y>1.4f){r=.19f;g=.39f;b=.20f;}
        else {r=.26f;g=.38f;b=.22f;}
        float variation=(float)(0.94+0.06*Math.sin(x*0.8+z*0.4));r*=variation;g*=variation;b*=variation;
        return new float[]{x,y,z,nx,ny,nz,r,g,b};
    }
    private static float height(float x,float z){
        double d=Math.sqrt(x*x+z*z);
        double edge=Math.max(0,Math.min(1,(68-d)/15.0));
        double base=2.0*Math.sin(x*.083)*Math.cos(z*.061)+1.6*Math.sin((x+z)*.105)+1.1*Math.cos(z*.17);
        double m1=20*Math.exp(-((x-14)*(x-14)/430.0+(z+18)*(z+18)/580.0));
        double m2=15*Math.exp(-((x+25)*(x+25)/620.0+(z-16)*(z-16)/460.0));
        return (float)(edge*Math.max(0,2+base+m1+m2));
    }
    private Mesh makeForest(int count){
        Builder b=new Builder(count*165);
        Random r=new Random(1254);
        for(int i=0;i<count;i++){
            float x,z,y; int tries=0;
            do{x=(r.nextFloat()*2-1)*62;z=(r.nextFloat()*2-1)*62;y=height(x,z);tries++;}
            while(tries<20 && (Math.sqrt(x*x+z*z)<15 || Math.sqrt(x*x+z*z)>62 || y>9));
            if(y<0.8f || y>9)continue;
            float scale=.72f+r.nextFloat()*.75f;
            float trunk=.20f*scale, th=1.3f*scale;
            addCone(b,x,y+th*.5f,z,trunk,th,7,0.28f,0.18f,0.10f);
            float green=.65f+r.nextFloat()*.25f;
            addCone(b,x,y+th+0.65f*scale,z,0.78f*scale,1.35f*scale,7,.055f,green,.18f);
            addCone(b,x,y+th+1.18f*scale,z,0.54f*scale,1.05f*scale,7,.07f,green*.92f,.21f);
            if(graphics>0 && i%3==0){
                float fx=x+(r.nextFloat()-.5f)*1.3f, fz=z+(r.nextFloat()-.5f)*1.3f;
                addCone(b,fx,y+.16f,fz,.07f,.3f,5,.25f,.52f,.22f);
            }
        }
        return b.finish();
    }
    private Mesh makeProps(){
        Builder b=new Builder(12000);
        addBox(b,21,height(21,-13)+1.3f,-13,1.5f,2.6f,1.5f,.38f,.43f,.44f);
        addBox(b,18.5f,height(18.5f,-13)+1.0f,-13,1.0f,2.0f,1.0f,.31f,.37f,.39f);
        addBox(b,23.5f,height(23.5f,-13)+.8f,-13,1.2f,1.6f,1.2f,.31f,.37f,.39f);
        for(int i=0;i<24;i++){
            double a=i*2.39996;float x=(float)Math.cos(a)*(8+(i%6)*3),z=(float)Math.sin(a)*(8+(i%6)*3),y=height(x,z);
            if(y>.8f && y<9f)addSphere(b,x,y+.25f,z,.35f+(i%3)*.18f,.28f+(i%4)*.13f,.31f+(i%2)*.15f,.31f,.34f,.33f);
        }
        // Ancient beacon plinth and broken monolith ring.
        addBox(b,0,height(0,-35)+.65f,-35,2.1f,1.3f,2.1f,.28f,.37f,.39f);
        addBox(b,0,height(0,-35)+2.25f,-35,0.65f,3.2f,0.65f,.16f,.39f,.39f);
        for(int i=0;i<7;i++){double a=i*Math.PI*2/7;float x=(float)Math.cos(a)*4,z=-35+(float)Math.sin(a)*4;
            addBox(b,x,height(x,z)+1,z,.45f,2.0f,.45f,.27f,.35f,.38f);}
        return b.finish();
    }
    private Mesh makeWater(){
        Builder b=new Builder(100);
        float s=145f, y=.20f, nx=0,ny=1,nz=0;
        b.vertex(-s,y,-s,nx,ny,nz,.035f,.25f,.36f);b.vertex(s,y,-s,nx,ny,nz,.035f,.25f,.36f);b.vertex(-s,y,s,nx,ny,nz,.035f,.25f,.36f);
        b.vertex(s,y,-s,nx,ny,nz,.035f,.25f,.36f);b.vertex(s,y,s,nx,ny,nz,.035f,.25f,.36f);b.vertex(-s,y,s,nx,ny,nz,.035f,.25f,.36f);
        return b.finish();
    }
    private Mesh makeBeacon(){
        Builder b=new Builder(3000);
        addCone(b,0,1,0,.14f,1.8f,6,.35f,.95f,.85f);
        return b.finish();
    }
    private Mesh makeCube(){
        Builder b=new Builder(400);
        float[][] faces={{-1,-1,1,1,-1,1,1,1,1,-1,1,1},{1,-1,-1,-1,-1,-1,-1,1,-1,1,1,-1},{-1,-1,-1,-1,-1,1,-1,1,1,-1,1,-1},{1,-1,1,1,-1,-1,1,1,-1,1,1,1},{-1,1,1,1,1,1,1,1,-1,-1,1,-1},{-1,-1,-1,1,-1,-1,1,-1,1,-1,-1,1}};
        float[][] ns={{0,0,1},{0,0,-1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
        for(int i=0;i<faces.length;i++){float[] f=faces[i],n=ns[i];b.quad(f[0]*.5f,f[1]*.5f,f[2]*.5f,f[3]*.5f,f[4]*.5f,f[5]*.5f,f[6]*.5f,f[7]*.5f,f[8]*.5f,f[9]*.5f,f[10]*.5f,f[11]*.5f,n[0],n[1],n[2],.8f,.8f,.8f);}
        return b.finish();
    }
    private Mesh makeSphere(int lat,int lon){
        Builder b=new Builder(lat*lon*54);
        for(int i=0;i<lat;i++){float v0=(float)i/lat,v1=(float)(i+1)/lat;float p0=(float)Math.PI*(v0-.5f),p1=(float)Math.PI*(v1-.5f);
            for(int j=0;j<lon;j++){float u0=(float)j/lon,u1=(float)(j+1)/lon;float a=u0*(float)Math.PI*2,bb=u1*(float)Math.PI*2;
                float[] vA=sphereV(p0,a),vB=sphereV(p0,bb),vC=sphereV(p1,a),vD=sphereV(p1,bb);
                b.vertex(vA[0],vA[1],vA[2],vA[0],vA[1],vA[2],.8f,.8f,.8f);b.vertex(vC[0],vC[1],vC[2],vC[0],vC[1],vC[2],.8f,.8f,.8f);b.vertex(vB[0],vB[1],vB[2],vB[0],vB[1],vB[2],.8f,.8f,.8f);
                b.vertex(vB[0],vB[1],vB[2],vB[0],vB[1],vB[2],.8f,.8f,.8f);b.vertex(vC[0],vC[1],vC[2],vC[0],vC[1],vC[2],.8f,.8f,.8f);b.vertex(vD[0],vD[1],vD[2],vD[0],vD[1],vD[2],.8f,.8f,.8f);
            }}
        return b.finish();
    }
    private float[] sphereV(float lat,float lon){float c=(float)Math.cos(lat);return new float[]{c*(float)Math.cos(lon),(float)Math.sin(lat),c*(float)Math.sin(lon)};}
    private Mesh makeCrystal(){
        Builder b=new Builder(500);
        float[][] v={{0,1,0},{.55f,0,.55f},{-.55f,0,.55f},{-.55f,0,-.55f},{.55f,0,-.55f},{0,-.8f,0}};
        int[][] t={{0,1,2},{0,2,3},{0,3,4},{0,4,1},{5,2,1},{5,3,2},{5,4,3},{5,1,4}};
        for(int[] a:t)b.triRaw(v[a[0]],v[a[1]],v[a[2]],.12f,.92f,.82f);
        return b.finish();
    }
    private void drawPlayer(){
        float bob=jumping?.05f*(float)Math.sin(time*14):.04f*(float)Math.sin(time*7);
        drawAt(cube,playerX,playerY+.9f+bob,playerZ,.65f,.95f,.42f,0,.25f,.43f,.62f);
        drawAt(sphere,playerX,playerY+1.65f+bob,playerZ,.29f,.31f,.29f,0,.88f,.76f,.58f);
        drawAt(cube,playerX-.42f,playerY+.9f+bob,playerZ,.22f,.72f,.23f,0,.20f,.32f,.43f);
        drawAt(cube,playerX+.42f,playerY+.9f+bob,playerZ,.22f,.72f,.23f,0,.20f,.32f,.43f);
        drawAt(cube,playerX-.19f,playerY+.28f,playerZ,.23f,.58f,.24f,0,.11f,.20f,.29f);
        drawAt(cube,playerX+.19f,playerY+.28f,playerZ,.23f,.58f,.24f,0,.11f,.20f,.29f);
    }
    private void drawShards(){
        for(int i=0;i<shardX.length;i++)if(!shardTaken[i]){
            float y=height(shardX[i],shardZ[i])+1.05f+.20f*(float)Math.sin(time*2.7f+i);
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,shardX[i],y,shardZ[i]);Matrix.rotateM(model,0,time*42+i*32,0,1,0);Matrix.scaleM(model,0,.64f,.78f,.64f);
            draw(crystal,model,2);
            if(graphics>1){drawAt(sphere,shardX[i],y,shardZ[i],.16f,.16f,.16f,0,.14f,.9f,.8f);}
        }
    }
    private void drawSentinels(){
        for(int i=0;i<8;i++)if(enemyHp[i]>0){
            float bob=.13f*(float)Math.sin(time*2+i);
            drawAt(cube,enemyX[i],height(enemyX[i],enemyZ[i])+.72f+bob,enemyZ[i],.66f,.85f,.62f,0,.23f,.29f,.32f);
            drawAt(sphere,enemyX[i],height(enemyX[i],enemyZ[i])+1.32f+bob,enemyZ[i],.39f,.31f,.39f,0,.8f,.24f,.16f);
            drawAt(sphere,enemyX[i]-.13f,height(enemyX[i],enemyZ[i])+1.36f+bob,enemyZ[i]+.28f,.075f,.07f,.07f,0,1f,.82f,.18f);
            drawAt(sphere,enemyX[i]+.13f,height(enemyX[i],enemyZ[i])+1.36f+bob,enemyZ[i]+.28f,.075f,.07f,.07f,0,1f,.82f,.18f);
        }
    }
    private void drawBeacon(){
        float y=height(0,-35)+3.3f;
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,0,y,-35);Matrix.rotateM(model,0,time*20,0,1,0);Matrix.scaleM(model,0,.8f,1.2f,.8f);draw(beacon,model,2);
        if(beaconActive){drawAt(sphere,0,y+1.4f,-35,.7f,.7f,.7f,0,.12f,1f,.72f);}
    }
    private void drawAt(Mesh mesh,float x,float y,float z,float sx,float sy,float sz,int rot,float r,float g,float b){
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);if(rot!=0)Matrix.rotateM(model,0,rot,0,1,0);Matrix.scaleM(model,0,sx,sy,sz);draw(mesh,model,0);
    }
    private float[] identity(){float[] m=new float[16];Matrix.setIdentityM(m,0);return m;}
    private void draw(Mesh mesh,float[] m,int material){
        if(mesh==null||mesh.count==0)return;
        GLES20.glUniformMatrix4fv(uModel,1,false,m,0);GLES20.glUniform1f(uMaterial,material);
        mesh.data.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,3,GLES20.GL_FLOAT,false,36,mesh.data);
        mesh.data.position(3);GLES20.glEnableVertexAttribArray(aNormal);GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,36,mesh.data);
        mesh.data.position(6);GLES20.glEnableVertexAttribArray(aColor);GLES20.glVertexAttribPointer(aColor,3,GLES20.GL_FLOAT,false,36,mesh.data);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.count);
    }
    private static int createProgram(String vs,String fs){
        int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glBindAttribLocation(p,0,"aPosition");GLES20.glBindAttribLocation(p,1,"aNormal");GLES20.glBindAttribLocation(p,2,"aColor");GLES20.glLinkProgram(p);
        int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException("GL program link failed: "+GLES20.glGetProgramInfoLog(p));return p;
    }
    private static int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException("GL shader failed: "+GLES20.glGetShaderInfoLog(s));return s;}

    private static void addBox(Builder b,float x,float y,float z,float sx,float sy,float sz,float r,float g,float bl){
        float x0=x-sx/2,x1=x+sx/2,y0=y-sy/2,y1=y+sy/2,z0=z-sz/2,z1=z+sz/2;
        boxFace(b,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,0,0,1,r,g,bl);
        boxFace(b,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,0,0,-1,r,g,bl);
        boxFace(b,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,-1,0,0,r,g,bl);
        boxFace(b,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,1,0,0,r,g,bl);
        boxFace(b,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,0,1,0,r,g,bl);
        boxFace(b,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,0,-1,0,r,g,bl);
    }
    private static void boxFace(Builder b,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz,float r,float g,float bl){
        b.vertex(ax,ay,az,nx,ny,nz,r,g,bl);b.vertex(bx,by,bz,nx,ny,nz,r,g,bl);b.vertex(cx,cy,cz,nx,ny,nz,r,g,bl);
        b.vertex(ax,ay,az,nx,ny,nz,r,g,bl);b.vertex(cx,cy,cz,nx,ny,nz,r,g,bl);b.vertex(dx,dy,dz,nx,ny,nz,r,g,bl);
    }
    private static void addCone(Builder b,float x,float y,float z,float radius,float h,int sides,float r,float g,float bl){
        float cy=y-h/2,ty=y+h/2;
        for(int i=0;i<sides;i++){
            double a=i*2*Math.PI/sides,bb=(i+1)*2*Math.PI/sides;
            float ax=x+(float)Math.cos(a)*radius,az=z+(float)Math.sin(a)*radius,bx=x+(float)Math.cos(bb)*radius,bz=z+(float)Math.sin(bb)*radius;
            b.triRaw(new float[]{ax,cy,az},new float[]{bx,cy,bz},new float[]{x,ty,z},r,g,bl);
            b.triRaw(new float[]{x,cy,z},new float[]{bx,cy,bz},new float[]{ax,cy,az},r*.8f,g*.8f,bl*.8f);
        }
    }
    private static void addSphere(Builder b,float x,float y,float z,float sx,float sy,float sz,float r,float g,float bl){
        int lat=5,lon=7;
        for(int i=0;i<lat;i++){float p0=(float)Math.PI*(i/(float)lat-.5f),p1=(float)Math.PI*((i+1)/(float)lat-.5f);
            for(int j=0;j<lon;j++){float a=(float)(j*Math.PI*2/lon),bb=(float)((j+1)*Math.PI*2/lon);
                float[] A=sph(p0,a,sx,sy,sz,x,y,z),B=sph(p0,bb,sx,sy,sz,x,y,z),C=sph(p1,a,sx,sy,sz,x,y,z),D=sph(p1,bb,sx,sy,sz,x,y,z);
                b.triRaw(A,B,C,r,g,bl);b.triRaw(B,D,C,r,g,bl);
            }}
    }
    private static float[] sph(float p,float a,float sx,float sy,float sz,float x,float y,float z){float c=(float)Math.cos(p);return new float[]{x+c*(float)Math.cos(a)*sx,y+(float)Math.sin(p)*sy,z+c*(float)Math.sin(a)*sz};}
    private static float distance(float x,float z,float a,float b){return (float)Math.sqrt((x-a)*(x-a)+(z-b)*(z-b));}

    private static final class Mesh {
        final FloatBuffer data; final int count;
        Mesh(float[] v,int n){count=n;ByteBuffer bb=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());data=bb.asFloatBuffer();data.put(v);data.position(0);}
    }
    private static final class Builder {
        float[] v;int n;
        Builder(int cap){v=new float[Math.max(4096,cap*9)];}
        void ensure(int add){if(n+add>v.length){float[] nv=new float[Math.max(v.length*2,n+add)];System.arraycopy(v,0,nv,0,n);v=nv;}}
        void vertex(float x,float y,float z,float nx,float ny,float nz,float r,float g,float b){ensure(9);v[n++]=x;v[n++]=y;v[n++]=z;v[n++]=nx;v[n++]=ny;v[n++]=nz;v[n++]=r;v[n++]=g;v[n++]=b;}
        void tri(float[] a,float[] b,float[] c){vertex(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8]);vertex(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8]);vertex(c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7],c[8]);}
        void triRaw(float[] a,float[] b,float[] c,float r,float g,float bl){float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;float l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(l<1e-5f)l=1;nx/=l;ny/=l;nz/=l;vertex(a[0],a[1],a[2],nx,ny,nz,r,g,bl);vertex(b[0],b[1],b[2],nx,ny,nz,r,g,bl);vertex(c[0],c[1],c[2],nx,ny,nz,r,g,bl);}
        void quad(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz,float r,float g,float bl){vertex(ax,ay,az,nx,ny,nz,r,g,bl);vertex(bx,by,bz,nx,ny,nz,r,g,bl);vertex(cx,cy,cz,nx,ny,nz,r,g,bl);vertex(ax,ay,az,nx,ny,nz,r,g,bl);vertex(cx,cy,cz,nx,ny,nz,r,g,bl);vertex(dx,dy,dz,nx,ny,nz,r,g,bl);}
        Mesh finish(){float[] out=new float[n];System.arraycopy(v,0,out,0,n);return new Mesh(out,n/9);}
    }
    private static final String VERTEX =
        "uniform mat4 uVP; uniform mat4 uModel; uniform float uTime; uniform float uMaterial; " +
        "attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aColor; " +
        "varying vec3 vColor; varying vec3 vNormal; varying vec3 vWorld; " +
        "void main(){ vec4 world=uModel*vec4(aPosition,1.0); if(uMaterial>0.5 && uMaterial<1.5){world.y+=sin(world.x*0.13+uTime*1.7)*0.09+cos(world.z*0.11+uTime)*0.06;} " +
        "vWorld=world.xyz; vNormal=normalize(aNormal); vColor=aColor; gl_Position=uVP*world; }";
    private static final String FRAGMENT =
        "precision mediump float; uniform vec3 uLight; uniform vec3 uFog; uniform vec3 uEye; uniform float uTime; uniform float uMaterial; uniform float uQuality; " +
        "varying vec3 vColor; varying vec3 vNormal; varying vec3 vWorld; " +
        "void main(){ vec3 n=normalize(vNormal); vec3 l=normalize(uLight); vec3 v=normalize(uEye-vWorld); float diff=max(dot(n,l),0.0); " +
        "float spec=pow(max(dot(reflect(-l,n),v),0.0),uMaterial>0.5?28.0:14.0); float ambient=0.28; vec3 col=vColor*(ambient+diff*0.82); " +
        "if(uMaterial>0.5 && uMaterial<1.5){float fres=pow(1.0-max(dot(n,v),0.0),2.0);col=mix(col,vec3(0.16,0.66,0.78),0.34+fres*0.4)+spec*0.4;} " +
        "if(uMaterial>1.5){col+=vec3(0.08,0.65,0.48)*(0.22+0.22*sin(uTime*3.0));col+=spec*0.6;} " +
        "float dist=length(uEye-vWorld); float fog=clamp((dist-42.0)/(uQuality>1.5?120.0:88.0),0.0,0.82); col=mix(col,uFog,fog); gl_FragColor=vec4(col,1.0); }";

    // Minimal, conservative GLES 2.0 shaders for drivers that reject the main shader.
    private static final String FALLBACK_VERTEX =
        "uniform mat4 uVP; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aColor; " +
        "varying vec3 vColor; varying vec3 vNormal; " +
        "void main(){ vColor=aColor; vNormal=aNormal; gl_Position=uVP*uModel*vec4(aPosition,1.0); }";
    private static final String FALLBACK_FRAGMENT =
        "precision mediump float; uniform vec3 uLight; varying vec3 vColor; varying vec3 vNormal; " +
        "void main(){ float light=0.32+0.68*max(dot(normalize(vNormal),normalize(uLight)),0.0); gl_FragColor=vec4(vColor*light,1.0); }";
}
