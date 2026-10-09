package pl.librushome.android;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.*;
import javax.net.ssl.HttpsURLConnection;

/** No cookies or authorization headers. Every redirect is checked before connecting. */
final class HttpsUpdateTransport implements UpdateTransport {
    private HttpsURLConnection open(String address)throws Exception{
        String url=address;
        for(int i=0;i<6;i++){
            if(!UpdatePolicy.safeHop(url))throw new IOException("Unsafe update host");
            HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","LibrusApp-Android-Updater");c.setRequestProperty("Accept-Encoding","identity");
            int code=c.getResponseCode();
            if(code==301||code==302||code==303||code==307||code==308){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Missing redirect");url=new URI(url).resolve(next).toString();continue;}
            if(code==404){c.disconnect();throw new NoRelease();}
            if(code!=200){c.disconnect();throw new IOException("Update request failed");}return c;
        }throw new IOException("Too many redirects");
    }
    public String manifest()throws Exception{
        HttpsURLConnection c=open(UpdatePolicy.MANIFEST_URL);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>UpdatePolicy.MAX_MANIFEST_BYTES)throw new IOException("Metadata too large");out.write(b,0,n);}return out.toString(StandardCharsets.UTF_8.name());
        }finally{c.disconnect();}
    }
    public void download(UpdateRelease release,File target,IntConsumer progress,BooleanSupplier cancelled)throws Exception{
        HttpsURLConnection c=open(release.url);
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(target)){
            long length=c.getContentLengthLong();if(length>0&&length!=release.size)throw new IOException("Unexpected length");
            byte[] b=new byte[65536];int n,last=-1;long bytes=0;
            while((n=in.read(b))!=-1){if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");bytes+=n;if(bytes>release.size)throw new IOException("APK too large");out.write(b,0,n);int p=(int)(bytes*100/release.size);if(p!=last){last=p;progress.accept(p);}}
            out.getFD().sync();if(bytes!=release.size)throw new IOException("Incomplete download");
        }finally{c.disconnect();}
    }
}
