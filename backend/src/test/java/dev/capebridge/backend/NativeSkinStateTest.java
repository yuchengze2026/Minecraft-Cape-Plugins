package dev.capebridge.backend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeSkinStateTest {
 public static final class Key{}
 public static final class LegacyWatcher {
  public boolean a(Key key){return true;}
  public Object b(Key key){return (byte)126;}
  public void b(Key key,Object value){}
 }
 @Test void legacyContainsAccessorMustNotBeMistakenForSkinByteGetter() throws Exception {
  var getter=NativeSkinState.method(LegacyWatcher.class,Object.class,new String[]{"get","b","a"},Key.class);
  assertEquals((byte)126,getter.invoke(new LegacyWatcher(),new Key()));
  var setter=NativeSkinState.method(LegacyWatcher.class,void.class,new String[]{"set","b"},Key.class,Object.class);
  assertEquals(void.class,setter.getReturnType());
 }
 @Test void nativeCapeUpdatesPreserveEveryOtherBitAndCanRestorePreference(){
  for(int i=0;i<256;i++){
   byte flags=(byte)i,hidden=NativeSkinState.withCape(flags,false);
   assertEquals(i&254,hidden&255);
   assertEquals(flags,NativeSkinState.withCape(hidden,(flags&1)!=0));
  }
 }
 @Test void maskedServerUpdatesMustNotEraseTheClientsOriginalCapePreference(){
  assertEquals(127,NativeCapeMask.remember((byte)126,(byte)127)&255);
  assertEquals(125,NativeCapeMask.remember((byte)124,(byte)127)&255);
  assertEquals(124,NativeCapeMask.remember((byte)125,(byte)126)&255);
  assertEquals(127,NativeCapeMask.remember((byte)127,null)&255);
 }
}
