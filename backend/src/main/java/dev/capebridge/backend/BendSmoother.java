package dev.capebridge.backend;

/** Limit cloth lift in body space, independently of the shoulder and directed yaw. */
final class BendSmoother {
 private boolean initialized;
 private float value;
 float sample(float target,float maxStep){
  if(!initialized){initialized=true;return value=target;}
  value+=CapeCloth.clamp(target-value,-maxStep,maxStep);
  return value;
 }
 void reset(){initialized=false;value=0;}
}
