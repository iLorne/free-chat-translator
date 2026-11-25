package com.chattranslator;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Zero-key MyMemory API adapter using RuneLite's injected HTTP and JSON clients. */
@Singleton
public final class MyMemoryClient
{
    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public MyMemoryClient(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient.newBuilder()
            .callTimeout(12, TimeUnit.SECONDS)
            .build();
        this.gson = gson;
    }

    public Call translate(String message, SourceLanguage language, TranslationCallback callback)
    {
        final String url = TranslationRequest.buildUrl(message, language);
        final Request request = new Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build();
        Call call = httpClient.newCall(request);
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                callback.onError("MyMemory couldn't be reached (or timed out)");
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response r = response)
                {
                    if (!r.isSuccessful())
                    {
                        callback.onError("MyMemory returned HTTP " + r.code());
                        return;
                    }
                    ResponseBody body = r.body();
                    if (body == null)
                    {
                        callback.onError("MyMemory returned an empty response");
                        return;
                    }
                    // Bound response memory even if the service or an intermediary
                    // returns an unexpectedly large or streaming JSON payload.
                    String json = BoundedResponseReader.readUtf8(body.byteStream());
                    JsonObject root = gson.fromJson(json, JsonObject.class);
                    if (root == null)
                    {
                        callback.onError("MyMemory returned no translation");
                        return;
                    }
                    if (root.has("quotaFinished") && root.get("quotaFinished").getAsBoolean())
                    {
                        callback.onError("MyMemory's daily free quota was reached");
                        return;
                    }
                    JsonElement status = root.get("responseStatus");
                    if (status == null || status.getAsInt() != 200)
                    {
                        callback.onError("MyMemory rejected the translation request");
                        return;
                    }
                    JsonObject data = root.getAsJsonObject("responseData");
                    if (data == null || !data.has("translatedText") || data.get("translatedText").isJsonNull())
                    {
                        callback.onError("MyMemory returned no translated text");
                        return;
                    }
                    String translation = data.get("translatedText").getAsString();
                    if (translation.trim().isEmpty())
                    {
                        callback.onError("MyMemory returned a blank translation");
                        return;
                    }
                    callback.onSuccess(TranslationRequest.forChatDisplay(translation));
                }
                catch (BoundedResponseReader.ResponseTooLargeException e)
                {
                    callback.onError("MyMemory returned an unexpectedly large response");
                }
                catch (Exception e)
                {
                    callback.onError("MyMemory response could not be read");
                }
            }
        });
        return call;
    }

    public interface TranslationCallback
    {
        void onSuccess(String translation);
        void onError(String message);
    }
}
