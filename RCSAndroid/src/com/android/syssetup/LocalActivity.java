package com.android.syssetup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class LocalActivity extends Activity implements OnSeekBarChangeListener {
	private SeekBar seekBar;
	private TextView textProgress;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	@Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    	//textProgress.setText("Compression Level: " + progress + "%");
    }
	
	@Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    	// TODO Auto-generated method stub
    }
	
	@Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    	// TODO Auto-generated method stub
    }
	
	public void startFakeActivity(View v) {
		Intent i = new Intent(this, SMain.class);

		// i.putExtra(PlayerService.EXTRA_PLAYLIST, "main");
		// i.putExtra(PlayerService.EXTRA_SHUFFLE, true);

		startService(i);
	}

	public void stopPlayer(View v) {
		stopService(new Intent(this, SMain.class));
	}
}
