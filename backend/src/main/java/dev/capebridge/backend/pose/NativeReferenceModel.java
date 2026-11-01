package dev.capebridge.backend.pose;

import com.google.gson.*;
import org.joml.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable reference calculation. This does not claim knowledge of a client's live frame inputs. */
public final class NativeReferenceModel {
    public record Input(boolean slim,boolean leftHand,float phase,float speed,float age,float headYaw,float headPitch,
                        boolean crouch,boolean passenger,boolean gliding,float attack,float swim,float speedDivisor,float previousHeadPitchRadians) {
        /** Compatibility default: callers with temporal data should supply the previous model head pitch explicitly. */
        public Input(boolean slim,boolean leftHand,float phase,float speed,float age,float headYaw,float headPitch,
                     boolean crouch,boolean passenger,boolean gliding,float attack,float swim,float speedDivisor){
            this(slim,leftHand,phase,speed,age,headYaw,headPitch,crouch,passenger,gliding,attack,swim,speedDivisor,headPitch*((float)java.lang.Math.PI/180f));
        }
        public Input {
            for(float value:new float[]{phase,speed,age,headYaw,headPitch,attack,swim,speedDivisor,previousHeadPitchRadians})if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite reference input");
            if(speedDivisor<1||attack<0||attack>1||swim<0||swim>1)throw new IllegalArgumentException("Reference input out of domain");
        }
    }
    public record Part(float x,float y,float z,float xRot,float yRot,float zRot,float xScale,float yScale,float zScale) {
        public float[] matrix(){return new Matrix4f().translation(x/16f,y/16f,z/16f).rotate(new Quaternionf().rotationZYX(zRot,yRot,xRot)).scale(xScale,yScale,zScale).get(new float[16]);}
    }
    private static final class Mutable {
        float x,y,z,rx,ry,rz,sx,sy,sz;
        Mutable(JsonObject p){x=f(p,"x");y=f(p,"y");z=f(p,"z");rx=f(p,"xRot");ry=f(p,"yRot");rz=f(p,"zRot");sx=f(p,"xScale");sy=f(p,"yScale");sz=f(p,"zScale");}
        Part freeze(){return new Part(x,y,z,rx,ry,rz,sx,sy,sz);}
    }
    private static final float PI=(float)java.lang.Math.PI;
    private static final float[] SIN=new float[65536];
    static{for(int i=0;i<SIN.length;i++)SIN[i]=(float)java.lang.Math.sin(i*java.lang.Math.PI*2/SIN.length);}
    private float sin(float value){return SIN[doublePrecisionTrig?(int)((long)(value*10430.378350470453d)&65535):(int)(value*10430.378f)&65535];}
    private float cos(float value){return SIN[doublePrecisionTrig?(int)((long)(value*10430.378350470453d+16384d)&65535):(int)(value*10430.378f+16384f)&65535];}
    private static float f(JsonObject object,String key){return object.get(key).getAsFloat();}
    private static float lerp(float amount,float a,float b){return a+amount*(b-a);}
    private static float rotlerp(float amount,float a,float b){float d=(b-a)%(PI*2);if(d < -PI)d+=PI*2;if(d>=PI)d-=PI*2;return a+amount*d;}
    private float bobX(float age){return sin(age*.067f)*.05f;}
    private float bobZ(float age){return cos(age*.09f)*.05f+.05f;}
    private final JsonObject model;
    private final boolean doublePrecisionTrig;
    private NativeReferenceModel(JsonObject model){this.model=model;doublePrecisionTrig=model.get("doublePrecisionTrig").getAsBoolean();}
    public static NativeReferenceModel load(String version)throws IOException{
        if(!version.matches("[0-9]+(?:\\.[0-9]+){1,2}"))throw new IllegalArgumentException("Invalid game version");
        try(InputStream in=NativeReferenceModel.class.getResourceAsStream("/pose-reference/"+version+".json")){
            if(in==null)throw new FileNotFoundException("Reference model unavailable: "+version);
            return new NativeReferenceModel(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }
    public Map<String,Part> evaluate(Input in){
        String key=(in.slim?"slim":"wide")+(in.crouch?"Crouch":"Stand");JsonObject prototype=model.getAsJsonObject("prototypes").getAsJsonObject(key);
        Map<String,Mutable> p=new LinkedHashMap<>();prototype.entrySet().forEach(e->p.put(e.getKey(),new Mutable(e.getValue().getAsJsonObject())));
        Mutable head=p.get("head"),body=p.get("body"),right=p.get("rightArm"),left=p.get("leftArm"),rightLeg=p.get("rightLeg"),leftLeg=p.get("leftLeg");
        head.rx=in.headPitch*(PI/180f);head.ry=in.headYaw*(PI/180f);
        if(in.gliding)head.rx=-PI/4;
        else if(in.swim>0)head.rx=rotlerp(in.swim,in.previousHeadPitchRadians,-PI/4);
        // Prototypes contain the native idle bob at age 40. Remove it before evaluating a new input.
        right.rx-=bobX(40);left.rx+=bobX(40);right.rz-=bobZ(40);left.rz+=bobZ(40);
        right.rx+=cos(in.phase*.6662f+PI)*2*in.speed*.5f/in.speedDivisor;
        left.rx+=cos(in.phase*.6662f)*2*in.speed*.5f/in.speedDivisor;
        rightLeg.rx=cos(in.phase*.6662f)*1.4f*in.speed/in.speedDivisor;
        leftLeg.rx=cos(in.phase*.6662f+PI)*1.4f*in.speed/in.speedDivisor;
        if(in.passenger){right.rx-=PI/5;left.rx-=PI/5;rightLeg.rx=leftLeg.rx=-1.4137167f;rightLeg.ry=PI/10;leftLeg.ry=-PI/10;rightLeg.rz=.07853982f;leftLeg.rz=-.07853982f;}
        if(in.attack>0){
            body.ry=sin((float)java.lang.Math.sqrt(in.attack)*(PI*2))*.2f*(in.leftHand?-1:1);
            right.z=sin(body.ry)*5;right.x=-cos(body.ry)*5;left.z=-sin(body.ry)*5;left.x=cos(body.ry)*5;
            right.ry+=body.ry;left.ry+=body.ry;left.rx+=body.ry;
            float progress=1-in.attack;progress*=progress;progress*=progress;progress=1-progress;
            Mutable hand=in.leftHand?left:right;
            hand.rx-=sin(progress*PI)*1.2f+sin(in.attack*PI)*-(head.rx-.7f)*.75f;
            hand.ry+=body.ry*2;hand.rz+=sin(in.attack*PI)*-.4f;
        }
        right.rx+=bobX(in.age);left.rx-=bobX(in.age);right.rz+=bobZ(in.age);left.rz-=bobZ(in.age);
        if(in.swim>0){
            if(in.attack>0)throw new IllegalArgumentException("Combined swim/attack is outside the verified reference domain");
            float phase=in.phase%26,amount=in.swim;
            if(phase<14){left.rx=rotlerp(amount,left.rx,0);right.rx=lerp(amount,right.rx,0);left.ry=rotlerp(amount,left.ry,PI);right.ry=lerp(amount,right.ry,PI);
                float q=(-65*phase+phase*phase)/(-65f*14+14f*14);left.rz=rotlerp(amount,left.rz,PI+1.8707964f*q);right.rz=lerp(amount,right.rz,PI-1.8707964f*q);}
            else if(phase<22){float t=(phase-14)/8;left.rx=rotlerp(amount,left.rx,(PI/2)*t);right.rx=lerp(amount,right.rx,(PI/2)*t);left.ry=rotlerp(amount,left.ry,PI);right.ry=lerp(amount,right.ry,PI);left.rz=rotlerp(amount,left.rz,5.012389f-1.8707964f*t);right.rz=lerp(amount,right.rz,1.2707963f+1.8707964f*t);}
            else{float t=(phase-22)/4;left.rx=rotlerp(amount,left.rx,PI/2-(PI/2)*t);right.rx=lerp(amount,right.rx,PI/2-(PI/2)*t);left.ry=rotlerp(amount,left.ry,PI);right.ry=lerp(amount,right.ry,PI);left.rz=rotlerp(amount,left.rz,PI);right.rz=lerp(amount,right.rz,PI);}
            leftLeg.rx=lerp(amount,leftLeg.rx,.3f*cos(in.phase*.33333334f+PI));rightLeg.rx=lerp(amount,rightLeg.rx,.3f*cos(in.phase*.33333334f));
        }
        Map<String,Part> output=new LinkedHashMap<>();p.forEach((name,part)->output.put(name,part.freeze()));return Collections.unmodifiableMap(output);
    }
    public Vector3f[] bodyBackTop(Map<String,Part> pose,boolean slim,boolean crouch){
        String key=(slim?"slim":"wide")+(crouch?"Crouch":"Stand");var box=model.getAsJsonObject("prototypes").getAsJsonObject(key).getAsJsonObject("body").getAsJsonArray("cubes").get(0).getAsJsonObject();
        Matrix4f transform=new Matrix4f().set(pose.get("body").matrix());float min=f(box,"minX")/16,max=f(box,"maxX")/16,y=f(box,"minY")/16,z=f(box,"maxZ")/16;
        return new Vector3f[]{transform.transformPosition(new Vector3f(min,y,z)),transform.transformPosition(new Vector3f((min+max)/2,y,z)),transform.transformPosition(new Vector3f(max,y,z))};
    }
}
