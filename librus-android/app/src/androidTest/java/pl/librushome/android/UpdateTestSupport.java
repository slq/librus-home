package pl.librushome.android;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

final class UpdateTestSupport {
    static Context context(){return HomeworkCompletionTest.context();}
    static File asset(String name)throws Exception{
        File file=new File(context().getCacheDir(),"fixture-"+name);try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);OutputStream out=new FileOutputStream(file)){byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)out.write(b,0,n);}return file;
    }
    static UpdateRelease release(File file)throws Exception{return release(file,19,"0.12.1");}
    static UpdateRelease release(File file,int code,String name)throws Exception{return UpdateRelease.parse(new JSONObject().put("schema",1).put("packageName","pl.librushome.android").put("versionCode",code).put("versionName",name).put("size",file.length()).put("sha256",UpdateApk.digest(file)).put("apkUrl","https://github.com/slq/librus-home/releases/download/android-v"+name+"/LibrusApp-android-"+name+".apk").put("notes","SYNTHETIC_RELEASE_NOTES").toString());}
    static void idle(UpdateManager m)throws Exception{m.worker.submit(()->{}).get(30,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    static UpdateTransport fake(UpdateRelease release,File file){return new UpdateTransport(){public String manifest(){return release.json();}public void download(UpdateRelease r,File target,java.util.function.IntConsumer p,java.util.function.BooleanSupplier cancelled)throws Exception{java.nio.file.Files.copy(file.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);p.accept(100);}};}
}
