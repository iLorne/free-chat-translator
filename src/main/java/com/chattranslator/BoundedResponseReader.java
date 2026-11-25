package com.chattranslator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Limits the free translation service's untrusted response before JSON parsing. */
final class BoundedResponseReader
{
    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private BoundedResponseReader() { }

    static String readUtf8(InputStream source) throws IOException
    {
        if (source == null)
        {
            throw new IOException("Missing MyMemory response stream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = source.read(buffer)) != -1)
        {
            if (read > MAX_RESPONSE_BYTES - out.size())
            {
                throw new ResponseTooLargeException();
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class ResponseTooLargeException extends IOException
    {
        private ResponseTooLargeException()
        {
            super("MyMemory response exceeded 64 KiB");
        }
    }
}
