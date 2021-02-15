package com.ginkage.bpmdetect;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.Nullable;

/** Helper class to manage the MediaProjection Service binding to an Activity. */
public class MediaProjectionServiceConnection {

    /** Interface for listening to the service binding event. */
    public interface ConnectionListener {
        /**
         * Callback that receives the service instance.
         *
         * @param service Service instance.
         */
        void onServiceConnected(MediaProjectionService service);
    }

    private final ServiceConnection connection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    if (service != null) {
                        MediaProjectionService.LocalBinder binder = (MediaProjectionService.LocalBinder) service;
                        MediaProjectionServiceConnection.this.service = binder.getService();
                        listener.onServiceConnected(MediaProjectionServiceConnection.this.service);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName className) {
                    service = null;
                }
            };

    private final ConnectionListener listener;
    private final Context context;

    @Nullable
    private MediaProjectionService service;
    private boolean bound;

    /**
     * @param context Activity that the service is bound to.
     * @param listener Callback to receive the service instance.
     */
    public MediaProjectionServiceConnection(Context context, ConnectionListener listener) {
        this.context = checkNotNull(context);
        this.listener = checkNotNull(listener);
    }

    /** Connects the activity to the service, starting it if required. */
    public void bind() {
        if (!bound) {
            Intent intent = new Intent(context, MediaProjectionService.class);
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            bound = true;
        }
    }

    /** Unbinds the service from the activity. */
    public void unbind() {
        if (service != null) {
            service.stopCapture();
            service = null;
        }
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
    }
}
