package dev.capebridge.web;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatActionsTest {
 @Test void linkKeepsFormattedTextAndExactDestination(){
  String url="http://127.0.0.1:27780/";
  var parts=WebMessages.clickable("§6[自定义] §f管理页："+url,ClickEvent.Action.OPEN_URL,url,"§e打开网页");
  assertEquals("[自定义] 管理页："+url,BaseComponent.toPlainText(parts));
  assertTrue(BaseComponent.toLegacyText(parts).contains("§6"));
  for(var part:parts){assertEquals(ClickEvent.Action.OPEN_URL,part.getClickEvent().getAction());assertEquals(url,part.getClickEvent().getValue());assertNotNull(part.getHoverEvent());}
 }
 @Test void codeCopiesOnlyTheCompleteCodeIncludingLeadingZeros(){
  String code="00123-AbC_987";
  var parts=WebMessages.clickable("§6登录码 §f"+code,ClickEvent.Action.COPY_TO_CLIPBOARD,code,"复制");
  assertEquals("登录码 "+code,BaseComponent.toPlainText(parts));
  for(var part:parts){assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD,part.getClickEvent().getAction());assertEquals(code,part.getClickEvent().getValue());}
 }
}
