package pl.librushome.android;

import java.net.URI;
import java.util.*;

/** Fixed publisher, HTTPS only; no Librus credentials or arbitrary update URLs. */
public final class UpdatePolicy {
    public static final String REPOSITORY="slq/librus-home";
    public static final String MANIFEST_URL="https://github.com/"+REPOSITORY+"/releases/latest/download/librus-android-update.json";
    public static final long CHECK_INTERVAL=24*60*60*1000L, MAX_APK_BYTES=150*1024*1024L;
    public static final int MAX_MANIFEST_BYTES=65536;
    private UpdatePolicy(){}
    public static boolean due(long now,long checked){return checked<=0||now<checked||now-checked>=CHECK_INTERVAL;}
    public static boolean safeHop(String value){
        try{URI u=new URI(value);String h=u.getHost();return "https".equalsIgnoreCase(u.getScheme())&&u.getUserInfo()==null&&u.getFragment()==null&&(u.getPort()==-1||u.getPort()==443)&&h!=null&&Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com").contains(h.toLowerCase(Locale.ROOT));}catch(Exception e){return false;}
    }
    public static boolean apkUrl(String value){
        try{URI u=new URI(value);return safeHop(value)&&"github.com".equalsIgnoreCase(u.getHost())&&u.getQuery()==null&&u.getRawPath().matches("/slq/librus-home/releases/download/android-v[0-9]+\\.[0-9]+\\.[0-9]+/LibrusApp-android-[0-9]+\\.[0-9]+\\.[0-9]+\\.apk");}catch(Exception e){return false;}
    }
}
