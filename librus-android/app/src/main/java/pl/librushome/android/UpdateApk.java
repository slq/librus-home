package pl.librushome.android;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.security.*;
import java.util.*;

/** Digest, package, version, SDK, release mode and current signer are all checked before install. */
final class UpdateApk {
    static String digest(File file)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}return hex(hash.digest());}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}
    static long code(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    @SuppressWarnings("deprecation") static int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    @SuppressWarnings("deprecation") static Set<String> signers(PackageInfo p)throws Exception{
        android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
        if(signatures==null||signatures.length==0)throw new SecurityException("Missing signer");Set<String> result=new HashSet<>();for(var s:signatures)result.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return result;
    }
    static void verify(Context context,File file,UpdateRelease release)throws Exception{
        if(!file.isFile()||file.length()!=release.size||!digest(file).equals(release.sha256))throw new SecurityException("APK digest mismatch");
        PackageManager pm=context.getPackageManager();PackageInfo own=pm.getPackageInfo(context.getPackageName(),flags());PackageInfo apk=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags());
        if(apk==null||!context.getPackageName().equals(apk.packageName)||code(apk)!=release.code||release.code<=code(own)||!release.name.equals(apk.versionName)||apk.applicationInfo==null||apk.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT||(apk.applicationInfo.flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0||!signers(own).equals(signers(apk)))throw new SecurityException("Incompatible APK");
    }
}
