package com.chattranslator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ResponseBoundsChecks
{
    private static int passed;

    private static void check(boolean condition, String description)
    {
        if (!condition) throw new AssertionError(description);
        passed++;
    }

    public static void main(String[] args) throws Exception
    {
        check("{}".equals(BoundedResponseReader.readUtf8(stream("{}"))), "empty JSON");
        check("¿Alguien?".equals(BoundedResponseReader.readUtf8(stream("¿Alguien?"))), "UTF-8 decoding");
        check("".equals(BoundedResponseReader.readUtf8(stream(""))), "empty response");
        byte[] max = new byte[BoundedResponseReader.MAX_RESPONSE_BYTES];
        Arrays.fill(max, (byte)'a');
        check(BoundedResponseReader.readUtf8(new ByteArrayInputStream(max)).length() == max.length,
            "exact 64 KiB allowed");
        byte[] over = Arrays.copyOf(max, max.length + 1);
        boolean rejected = false;
        try { BoundedResponseReader.readUtf8(new ByteArrayInputStream(over)); }
        catch (BoundedResponseReader.ResponseTooLargeException expected) { rejected = true; }
        check(rejected, "oversize streaming rejected");
        rejected = false;
        try { BoundedResponseReader.readUtf8(null); }
        catch (IOException expected) { rejected = true; }
        check(rejected, "null stream rejected");
        rejected = false;
        try
        {
            BoundedResponseReader.readUtf8(new InputStream()
            {
                public int read() throws IOException { throw new IOException("synthetic failure"); }
            });
        }
        catch (IOException expected) { rejected = true; }
        check(rejected, "stream I/O errors propagated");
        System.out.println("ResponseBoundsChecks: " + passed + "/" + passed + " PASS");
    }

    private static ByteArrayInputStream stream(String s)
    {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
