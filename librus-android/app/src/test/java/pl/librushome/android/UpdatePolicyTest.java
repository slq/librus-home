package pl.librushome.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    @Test public void updateUrlIsBoundToOurRepositoryAndReleaseApk(){
        assertTrue(UpdatePolicy.apkUrl("https://github.com/slq/librus-home/releases/download/android-v0.12.1/LibrusApp-android-0.12.1.apk"));
        for(String url:new String[]{"http://github.com/slq/librus-home/releases/download/android-v0.12.1/LibrusApp-android-0.12.1.apk","https://github.com/evil/librus-home/releases/download/android-v0.12.1/LibrusApp-android-0.12.1.apk","https://github.com/slq/librus-home/releases/download/android-v0.12.1/../malicious.apk","https://github.com/slq/librus-home/releases/download/android-v0.12.1/LibrusApp-android-0.12.1.apk?token=secret"})assertFalse(url,UpdatePolicy.apkUrl(url));
    }
    @Test public void redirectsRejectCleartextCredentialsPortsAndLookalikeHosts(){
        assertTrue(UpdatePolicy.safeHop("https://release-assets.githubusercontent.com/a?sig=temporary"));
        for(String url:new String[]{"http://github.com/a","https://github.com.evil.invalid/a","https://user:pass@github.com/a","https://github.com:8443/a","https://127.0.0.1/a","file:///data/data/state.aes"})assertFalse(url,UpdatePolicy.safeHop(url));
    }
    @Test public void automaticChecksHaveDailyCadenceAndRecoverFromClockRollback(){
        assertTrue(UpdatePolicy.due(100,0));assertFalse(UpdatePolicy.due(10001,10000));assertTrue(UpdatePolicy.due(10000+UpdatePolicy.CHECK_INTERVAL,10000));assertTrue(UpdatePolicy.due(500,10000));
    }
}
