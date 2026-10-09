package pl.librushome.android;
import org.json.*;

/** Strict public metadata; notes are plain text and never executed. */
public final class UpdateRelease {
    public final long code,size;public final String name,url,sha256,notes;
    private UpdateRelease(long code,String name,String url,String hash,long size,String notes){this.code=code;this.name=name;this.url=url;sha256=hash;this.size=size;this.notes=notes;}
    private static long integer(JSONObject o,String key)throws JSONException{Object v=o.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new JSONException("Invalid integer");return ((Number)v).longValue();}
    public static UpdateRelease parse(String raw)throws JSONException{
        if(raw==null||raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>UpdatePolicy.MAX_MANIFEST_BYTES)throw new JSONException("Metadata too large");JSONObject o=new JSONObject(raw);
        if(integer(o,"schema")!=1||!"pl.librushome.android".equals(o.getString("packageName")))throw new JSONException("Invalid package");
        long code=integer(o,"versionCode"),size=integer(o,"size");String name=o.getString("versionName"),url=o.getString("apkUrl"),hash=o.getString("sha256"),notes=o.getString("notes");
        if(code<=0||code>Integer.MAX_VALUE||size<=0||size>UpdatePolicy.MAX_APK_BYTES||!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!hash.matches("[a-f0-9]{64}")||notes.length()>16000||!UpdatePolicy.apkUrl(url)||!url.endsWith("/LibrusApp-android-"+name+".apk")||!url.contains("/android-v"+name+"/"))throw new JSONException("Invalid release");
        return new UpdateRelease(code,name,url,hash,size,notes);
    }
    public String json(){try{return new JSONObject().put("schema",1).put("packageName","pl.librushome.android").put("versionCode",code).put("versionName",name).put("apkUrl",url).put("sha256",sha256).put("size",size).put("notes",notes).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
}
