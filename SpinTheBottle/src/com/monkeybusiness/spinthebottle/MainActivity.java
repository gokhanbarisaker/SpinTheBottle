package com.monkeybusiness.spinthebottle;

import com.monkeybusiness.spinthebottle.SpinningDrawableView.OnStartRotatingListener;
import com.monkeybusiness.spinthebottle.SpinningDrawableView.OnStopRotatingListener;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends Activity 
{
	private static final String TAG = "SpinningDrawableViewTest";
	
	SpinningDrawableView bottleView;
	
	int bottleIndex = 0;
	int[] bottleResourceIds = {R.drawable.bottle0, R.drawable.bottle1, R.drawable.bottle2, R.drawable.bottle3};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        fetchReferencesFromXML();
        
        bottleView.setOnStartRotatingListener(new OnStartRotatingListener() 
        {	
			public void onStart(float angularSpeed) 
			{
				Log.d(TAG, "starting to rotate with " + angularSpeed);	
			}
		});
        
        bottleView.setOnStopRotatingListener(new OnStopRotatingListener() 
        {	
			public void onStop(float stopAngle) 
			{
				Log.d(TAG, "stopped at " + stopAngle);
			}
		});
    }

	private void fetchReferencesFromXML() 
	{
		bottleView = (SpinningDrawableView) findViewById(R.id.spinningDrawableView1);
		
		findViewById(R.id.button_changebottle).setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				bottleIndex = ++bottleIndex % bottleResourceIds.length;
				
				int bottleId = bottleResourceIds[bottleIndex];
				
				bottleView.setResourceDrawable(bottleId);
			}
		});
		
		findViewById(R.id.button_currentrotationdegree).setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				float degree = bottleView.getRotationDegree();
				
				Toast.makeText(MainActivity.this, "Bottle @" + degree, Toast.LENGTH_SHORT).show();
			}
		});
	}
}