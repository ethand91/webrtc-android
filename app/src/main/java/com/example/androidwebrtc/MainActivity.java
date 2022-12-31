package com.example.androidwebrtc;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.androidwebrtc.webrtc.Connection;
import com.example.androidwebrtc.webrtc.ConnectionListener;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements ConnectionListener {
    private static final String TAG = "MainActivity";
    private static final String WS_URI = "wss://192.168.0.109:8888";
    // WARNING: Turn this to false for production
    private static final boolean IS_DEBUG = true;
    private static final int CAMERA_AND_MIC = 1001;

    private WebSocketClient socket;
    private SurfaceViewRenderer mLocalRenderer;
    private SurfaceViewRenderer mRemoteRenderer;
    private EditText mPeerIdEditText;
    private Button mCallButton;
    private Button mLogoutButton;
    private Connection mConnection;
    private String mRemoteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocalRenderer = findViewById(R.id.localRenderer);
        mRemoteRenderer = findViewById(R.id.remoteRenderer);
        mPeerIdEditText = findViewById(R.id.peerIdEditText);
        mCallButton = findViewById(R.id.callButton);
        mLogoutButton = findViewById(R.id.logoutButton);

        mConnection = Connection.initialize(this, this);

        initializeCallButton();
        connectToWebsocketServer();
        initializeLogoutButton();
    }

    private void initializeCallButton() {
        mCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPeerIdEditText.getText().toString().trim().length() == 0) return;

                mRemoteId = mPeerIdEditText.getText().toString();
                mPeerIdEditText.setVisibility(View.INVISIBLE);
                mCallButton.setVisibility(View.INVISIBLE);
                mRemoteRenderer.setVisibility(View.VISIBLE);
                mLocalRenderer.setVisibility(View.VISIBLE);
                mLogoutButton.setVisibility(View.VISIBLE);
                Log.d(TAG, "Remote id " + mRemoteId);

                mConnection.createPeerConnection();
                mConnection.createOffer();

                view.clearFocus();
            }
        });
    }

    private void connectToWebsocketServer() {
        try {
            this.socket = new WebSocketClient(new URI(WS_URI)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.d(TAG, "onOpen");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mCallButton.setEnabled(true);
                        }
                    });
                    handleSocketOnOpen();
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "onMessage message=" + message);
                    handleWebSocketMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "onClose reason=" + reason);
                    MainActivity.this.closeConnection();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "onError", ex);
                }
            };

            if (IS_DEBUG) {
                Log.w(TAG, "Enabling debug mode");
                final SSLSocketFactory factory = supportSelfSignedCert();
                HttpsURLConnection.setDefaultSSLSocketFactory(factory);
                this.socket.setSocketFactory(factory);
            }

            this.socket.connect();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void initializeLogoutButton() {
        mLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeConnection();
            }
        });
    }

    private void closeConnection() {
        mConnection.close();

        mLocalRenderer.release();
        mRemoteRenderer.release();

        mPeerIdEditText.setVisibility(View.VISIBLE);
        mCallButton.setVisibility(View.VISIBLE);
        mRemoteRenderer.setVisibility(View.INVISIBLE);
        mLocalRenderer.setVisibility(View.INVISIBLE);
        mLogoutButton.setVisibility(View.INVISIBLE);
    }

    private void handleSocketOnOpen() {
        requestCameraAndMicAccess();
    }

    private SSLSocketFactory supportSelfSignedCert() throws NoSuchAlgorithmException, KeyManagementException {
        final TrustManager[] trustManagers = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };

        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
        final SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, trustManagers, new SecureRandom());

        return context.getSocketFactory();
    }

    private void handleWebSocketMessage(final String message) {
        Log.d(TAG, "Got server message:" + message);
        try {
           final JSONObject jsonMessage = new JSONObject(message);
           final String action = jsonMessage.getString("action");

           switch(action) {
               case "start":
                   Log.d(TAG, "WebSocket::start");
                   // TODO: Deplace in text
                   Log.d(TAG, "Local ID = " + jsonMessage.getString("id"));
                   break;
               case "offer":
                   Log.d(TAG, "WebSocket::offer " + jsonMessage.getJSONObject("data"));
                   mRemoteId = jsonMessage.getJSONObject("data").getString("remoteId");

                   initializePeerConnection(jsonMessage.getJSONObject("data").getJSONObject("offer").getString("sdp"));
                   break;
               case "answer":
                   Log.d(TAG, "WebSocket::answer");
                   mConnection.setRemoteDescription(jsonMessage.getJSONObject("data").getJSONObject("answer").getString("sdp"));
                   break;
               case "iceCandidate":
                   Log.d(TAG, "WebSocket::iceCandidate " + jsonMessage.getJSONObject("data").getJSONObject("candidate").toString());
                    mConnection.addRemoteIceCandidate(jsonMessage.getJSONObject("data").getJSONObject("candidate"));
                   break;
               default: Log.w(TAG, "WebSocket unknown action" + action);
           }
        } catch (JSONException je) {
            Log.e(TAG, "Failed to handle WebSocket message", je);
        }
    }

    private void initializePeerConnection(final String remoteOffer) {
        //mConnection.createPeerConnection();
        mConnection.createAnswerFromRemoteOffer(remoteOffer);
    }

    private void sendSocketMessage(final String action, final JSONObject data) {
        try {
            final JSONObject message = new JSONObject();
            message.put("action", action);
            message.put("data", data);

            socket.send(message.toString());
        } catch (JSONException je) {
            Log.e(TAG, je.toString());
        }
    }

    @AfterPermissionGranted(CAMERA_AND_MIC)
    private void requestCameraAndMicAccess() {
        String[] permissions = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
        if(EasyPermissions.hasPermissions(this, permissions)) {
            Log.d(TAG, "media permissions granted");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getUserMedia();
                }
            });
        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.request_camera_mic_permissions_text), CAMERA_AND_MIC, permissions);
        }
    }

    private void getUserMedia() {
        try {
            Log.d(TAG, "getUserMedia");
            mConnection.initializeMediaDevices(this, mLocalRenderer);

            final JSONObject data = new JSONObject();
            data.put("action", "start");

            sendSocketMessage("start", data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get camera device", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onAddStream(MediaStreamTrack mediaStreamTrack) {
        Log.d(TAG, "onAddStream " + mediaStreamTrack.kind());
        mediaStreamTrack.setEnabled(true);

        if (mediaStreamTrack.kind().equals("video")) {
            Log.d(TAG, "add video");
            final VideoTrack videoTrack = (VideoTrack) mediaStreamTrack;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final EglBase.Context eglContext = EglBase.create().getEglBaseContext();

                    mRemoteRenderer.init(eglContext, new RendererCommon.RendererEvents() {
                        @Override
                        public void onFirstFrameRendered() {

                        }

                        @Override
                        public void onFrameResolutionChanged(int i, int i1, int i2) {

                        }
                    });

                    videoTrack.addSink(mRemoteRenderer);
                }
            });
        }
    }

    @Override
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        try {
            final JSONObject candidate = new JSONObject();
            candidate.put("sdp", iceCandidate.sdp);
            candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            candidate.put("sdpMid", iceCandidate.sdpMid);

            final JSONObject data = new JSONObject();
            data.put("action", "iceCandidate");
            data.put("remoteId", mRemoteId);
            data.put("candidate", candidate);

            sendSocketMessage("iceCandidate", data);
        } catch (JSONException je) {
            Log.e(TAG, "Failed to handle onIceCandidate event", je);
        }
    }

    @Override
    public void onLocalOffer(SessionDescription offer) {
        Log.d(TAG, "onLocalOffer offer=" + offer);
        try {
            final JSONObject sdp = new JSONObject();
            sdp.put("type", "offer");
            sdp.put("sdp", offer.description);

            final JSONObject data = new JSONObject();
            data.put("action", offer.type);
            data.put("remoteId", mRemoteId);
            data.put("offer", sdp);

            sendSocketMessage("offer", data);
        } catch (JSONException je) {
           Log.e(TAG, "Failed to handle onLocalOffer", je);
        }
    }

    @Override
    public void onLocalAnswer(SessionDescription answer) {
        Log.d(TAG, "onLocalAnswer answer=" + answer);
        try {
            final JSONObject sdp = new JSONObject();
            sdp.put("type", "answer");
            sdp.put("sdp", answer.description);

            final JSONObject data = new JSONObject();
            data.put("action", "answer");
            data.put("remoteId", mRemoteId);
            data.put("answer", sdp);

            sendSocketMessage("answer", data);
        } catch (JSONException je) {
            Log.e(TAG, "Failed to handle onLocalAnswer", je);
        }
    }
}