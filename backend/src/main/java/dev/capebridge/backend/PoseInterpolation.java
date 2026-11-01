package dev.capebridge.backend;
import org.bukkit.util.Transformation;
/** Interpolation policy for the compensated, recentered cape model. */
final class PoseInterpolation {
 static int duration(Transformation from,Transformation to,boolean postureChanged){
  if(from==null||postureChanged)return 0;
  // The recentered model carries most of the shoulder arc. Only an ambiguous
  // exact half-turn or a discontinuity needs immediate placement.
  var a=from.getLeftRotation();var b=to.getLeftRotation();
  // Component arithmetic avoids the removed Quaternionf-specific overload in newer JOML.
  if(Math.abs(a.x*b.x+a.y*b.y+a.z*b.z+a.w*b.w)<.000001f)return 0;
  if(from.getTranslation().distanceSquared(to.getTranslation())>.25f)return 0;
  return 1;
 }
}
