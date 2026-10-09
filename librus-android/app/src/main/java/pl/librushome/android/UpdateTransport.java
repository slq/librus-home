package pl.librushome.android;
import java.io.File;
import java.util.function.*;

interface UpdateTransport {
    String manifest()throws Exception;
    void download(UpdateRelease release,File target,IntConsumer progress,BooleanSupplier cancelled)throws Exception;
    class NoRelease extends java.io.IOException{}
}
