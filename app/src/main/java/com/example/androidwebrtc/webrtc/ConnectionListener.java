package com.example.androidwebrtc.webrtc;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStreamTrack;
import org.webrtc.SessionDescription;

public interface ConnectionListener {
    void onIceCandidateReceived(IceCandidate iceCandidate);
    void onAddStream(MediaStreamTrack mediaStreamTrack);
    void onLocalOffer(SessionDescription offer);
    void onLocalAnswer(SessionDescription answer);
}
