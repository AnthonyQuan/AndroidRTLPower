package com.sdrtouch.rtlsdr;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

public class DialogGPSInternet extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        //build the dialog
        builder.setMessage("Please enable GPS and an internet connection, then click on RUN NOW")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // place code here if user needs to do something
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}