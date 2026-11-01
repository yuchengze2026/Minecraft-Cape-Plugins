package dev.capebridge.proxy;
import dev.capebridge.common.Wire;
import java.util.function.Predicate;
final class CapeAccess {
    static String node(String id){return "capebridge.cape."+id;}
    static boolean allowed(String id,Predicate<String> permission){return permission.test("capebridge.cape.all")||permission.test(node(id));}
    static Wire.Cape forPlayer(Wire.Cape cape,Predicate<String> permission){
        return cape.access(allowed(cape.id(),permission)).wardrobe(cape.wardrobeVisible()||permission.test("capebridge.cape.all"));
    }
}
