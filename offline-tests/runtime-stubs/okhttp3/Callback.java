package okhttp3;import java.io.IOException;public interface Callback {void onFailure(Call c,IOException e);void onResponse(Call c,Response r);}
