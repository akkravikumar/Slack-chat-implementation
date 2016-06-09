package ca.nickadams.slack.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import ca.nickadams.slack.R;
import ca.nickadams.slack.api.SlackApi;
import ca.nickadams.slack.models.Auth;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;


public class MainActivity extends AppCompatActivity {

    private String secretClient = "60e28e71e3737e79d138da9c1a25d77b";
    private String clientId = "17383527472.47619955284";
    private FrameLayout myWebContainer;
    private WebView myWebView;
    ArrayList<NameValuePair> params = new ArrayList<>();
    private String urlToRequest = "https://slack.com/api/oauth.access";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ButterKnife.inject(this);

        myWebContainer = (FrameLayout) findViewById(R.id.web_container);
        myWebView = (WebView) findViewById(R.id.webview);

        myWebView.setWebViewClient(new WebViewClient( ) {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if(url.indexOf("code=")!=-1){
                    int posStart = url.indexOf("code=");
                    int posEnd = url.indexOf("&state=");
                    String code = url.substring(posStart+5, posEnd);
                    Log.d("SlackAPICode",code);
                    // Création du tableau de params pour la requète d'authentification
                    params.add(new BasicNameValuePair("client_id", clientId));
                    params.add(new BasicNameValuePair("client_secret", secretClient));
                    params.add(new BasicNameValuePair("code", code));
                    new ConnectSlackRequest(params, MainActivity.this, urlToRequest).execute("");
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.d("TAG", "failed: " + failingUrl + ", error code: " + errorCode + " [" + description + "]");
            }
        });
        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.loadUrl("https://slack.com/oauth/authorize?client_id="+clientId+"&scope=channels:read,channels:write,channels:history,chat:write:user,chat:write:bot,team:read,users:read");

        if (SlackApi.getInstance(this).hasToken()) {
            testAuthToken();
        }
    }

    public class ConnectSlackRequest extends AsyncTask<String, Void, String> {
        private ArrayList<NameValuePair> params;
        private String urlRequest;
        //private Intent intent;
        WeakReference<Activity> activityReferenceStart;

        ConnectSlackRequest(ArrayList<NameValuePair> params, Activity activityStart, String url){
            this.params = params;
            //this.intent = intent;
            this.activityReferenceStart = new WeakReference<Activity>(activityStart);
            this.urlRequest = url;
        }

        @Override
        protected String doInBackground(String... message) {
            HttpClient httpclient;
            HttpPost request;
            HttpResponse response = null;
            String result = "";

            try {
                httpclient = new DefaultHttpClient();
                request = new HttpPost(urlRequest);
                request.setEntity(new UrlEncodedFormEntity(this.params));
                response = httpclient.execute(request);
            }
            catch (Exception e) {
                result = "error1";
            }

            try {
                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                String line = "";
                while ((line = rd.readLine()) != null)
                {
                    result = result + line ;
                }
            } catch (Exception e) {
                result = "error2";
            }
            //httpEntity = response.getEntity();
            //result = EntityUtils.toString(httpEntity);
            return result;
        }

        protected void onPostExecute(String result)  {
            try {
                JSONObject jsonResult = new JSONObject(result);
                boolean ok = jsonResult.getBoolean("ok");
                if(ok==true){
                    String token = jsonResult.getString("access_token");
                    if (activityReferenceStart.get() != null) {
                        Log.d("SlackAPICode", token);
                        if (!TextUtils.isEmpty(token)) {
                            SlackApi.getInstance(MainActivity.this).setAuthToken(token);
                            testAuthToken();
                        }

                        /*Activity activityS = activityReferenceStart.get();
                        Intent intent = new Intent(activityS, ListPostActivity.class);
                        intent.putExtra("token", token);
                        activityS.startActivity(intent);*/
                    }else{
                        Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                        //Activity activityS = activityReferenceStart.get();
                        // Intent intent = new Intent(activityS, MainActivity.class);
                        // activityS.startActivity(intent);
                    }
                }
            } catch (Exception e) {
                Activity activityS = activityReferenceStart.get();
                Intent intent = new Intent(activityS, MainActivity.class);
                activityS.startActivity(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        myWebContainer.removeAllViews();
        myWebView.clearHistory();
        myWebView.clearCache(true);
        myWebView.clearView();
        myWebView.destroy();
        myWebView = null;
    }

    private void testAuthToken() {
        SlackApi.getInstance(this).getSlackService().testAuth(new Callback<Auth>() {
            @Override
            public void success(Auth auth, Response response) {
                if (auth.ok) {
                    SlackApi.getInstance(MainActivity.this).setSelf(auth);
                    startActivity(ChannelsActivity.intentForAuth(MainActivity.this, auth));
                    Toast.makeText(MainActivity.this, auth.team + " " + auth.user, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(MainActivity.this, R.string.auth_token_invalid, Toast.LENGTH_SHORT).show();
                    SlackApi.getInstance(MainActivity.this).setAuthToken(null);
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Toast.makeText(MainActivity.this, R.string.auth_token_invalid, Toast.LENGTH_SHORT).show();
                SlackApi.getInstance(MainActivity.this).setAuthToken(null);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
