package dev.capebridge.backend.pose;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeReferenceGoldenTest {
    @Test void portableCalculationMatchesOfficialNativePoseCorpus()throws Exception{
        Path corpus=Path.of(System.getProperty("basedir"),"../collector/corpus").normalize();
        int cases=0,versions=0;double maxError=0,maxMatrixError=0;List<String> failures=new ArrayList<>();
        try(var files=Files.list(corpus)){
            for(Path file:files.filter(p->p.toString().endsWith(".jsonl")).sorted().toList()){
                String version=file.getFileName().toString().replace(".jsonl","");var model=NativeReferenceModel.load(version);versions++;
                try(var reader=Files.newBufferedReader(file)){String line;
                    while((line=reader.readLine())!=null){
                        var golden=JsonParser.parseString(line).getAsJsonObject();var s=golden.getAsJsonObject("input");
                        var input=new NativeReferenceModel.Input(golden.get("slim").getAsBoolean(),golden.get("mainArm").getAsString().equals("LEFT"),s.get("phase").getAsFloat(),s.get("speed").getAsFloat(),s.has("age")?s.get("age").getAsFloat():40,s.get("yaw").getAsFloat(),s.get("pitch").getAsFloat(),s.get("crouch").getAsBoolean(),s.get("riding").getAsBoolean(),s.get("gliding").getAsBoolean(),s.get("attack").getAsFloat(),s.get("swim").getAsFloat(),1);
                        var output=model.evaluate(input);cases++;
                        for(var entry:output.entrySet()){
                            var actual=entry.getValue();var expected=golden.getAsJsonObject("parts").getAsJsonObject(entry.getKey());
                            float[] values={actual.x(),actual.y(),actual.z(),actual.xRot(),actual.yRot(),actual.zRot(),actual.xScale(),actual.yScale(),actual.zScale()};
                            String[] keys={"x","y","z","xRot","yRot","zRot","xScale","yScale","zScale"};
                            for(int i=0;i<values.length;i++){
                                double error=java.lang.Math.abs(values[i]-expected.get(keys[i]).getAsFloat());maxError=java.lang.Math.max(maxError,error);
                                if(error>.00002&&failures.size()<20)failures.add(version+" "+s+" "+entry.getKey()+"."+keys[i]+" "+values[i]+" != "+expected.get(keys[i]));
                            }
                            float[] matrix=actual.matrix();var nativeMatrix=expected.getAsJsonArray("nativeLocalMatrix");
                            for(int i=0;i<16;i++){
                                double error=java.lang.Math.abs(matrix[i]-nativeMatrix.get(i).getAsFloat());maxMatrixError=java.lang.Math.max(maxMatrixError,error);
                                if(error>.000002&&failures.size()<20)failures.add(version+" "+s+" "+entry.getKey()+" matrix["+i+"] error="+error);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Reference golden versions="+versions+" cases="+cases+" maxError="+maxError+" maxMatrixError="+maxMatrixError);
        assertEquals(23,versions,"Native corpus must cover all target versions");assertEquals(18308,cases);assertTrue(failures.isEmpty(),String.join("\n",failures));
    }
    @Test void invalidAndUnverifiedInputDoesNotSilentlyProduceAReferencePose()throws Exception{
        assertThrows(IllegalArgumentException.class,()->NativeReferenceModel.load("../../config"));
        var model=NativeReferenceModel.load("1.20.1");
        assertThrows(IllegalArgumentException.class,()->new NativeReferenceModel.Input(false,false,Float.NaN,0,0,0,0,false,false,false,0,0,1));
        assertThrows(IllegalArgumentException.class,()->model.evaluate(new NativeReferenceModel.Input(false,false,0,0,0,0,0,false,false,false,.5f,1,1)));
        assertThrows(IllegalArgumentException.class,()->new NativeReferenceModel.Input(false,false,0,0,0,0,0,false,false,false,0,.5f,1,Float.NaN));
    }
    @Test void swimTransitionUsesPreviousHeadPitch()throws Exception{
        var model=NativeReferenceModel.load("1.20.1");
        var input=new NativeReferenceModel.Input(false,false,0,0,40,0,-60,false,false,false,0,.25f,1,0);
        assertEquals(-(float)Math.PI/16,model.evaluate(input).get("head").xRot(),0.000001);
    }
}
