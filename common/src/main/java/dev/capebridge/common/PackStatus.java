package dev.capebridge.common;
/** Connection-scoped pack progress; OFFERING is a grant, never a client status. */
public enum PackStatus {
 UNKNOWN,OFFERING,PENDING,ACCEPTED,DOWNLOADED,LOADED,DECLINED,FAILED,DISCARDED;
 public boolean reportable(){return this!=UNKNOWN&&this!=OFFERING;}
 public boolean terminal(){return this==LOADED||this==DECLINED||this==FAILED||this==DISCARDED;}
 public boolean canFollow(PackStatus previous){
  if(!reportable()||this==previous)return false;
  if(previous.terminal())return previous==LOADED&&(this==DISCARDED||this==FAILED);
  if(terminal())return true;
  return ordinal()>previous.ordinal();
 }
}
