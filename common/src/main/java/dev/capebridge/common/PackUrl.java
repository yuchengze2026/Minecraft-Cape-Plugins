package dev.capebridge.common;
/** Distinct cache keys for distinct pack contents; preserve administrator query parameters. */
public final class PackUrl {
    private PackUrl(){}
    public static String versioned(String url,String hash){
        if(url.isBlank())return url;
        if(!hash.matches("[a-fA-F0-9]{40}"))throw new IllegalArgumentException("Invalid pack SHA-1");
        int fragment=url.indexOf('#');String suffix=fragment<0?"":url.substring(fragment);
        String base=fragment<0?url:url.substring(0,fragment);
        return base+(base.contains("?")?(base.endsWith("?")||base.endsWith("&")?"":"&"):"?")+"capebridge="+hash.toLowerCase(java.util.Locale.ROOT)+suffix;
    }
}
