/* 
 *  Copyright 2012 Gökhan Barış Aker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.monkeybusiness.spinthebottle;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

public class SpinningDrawableView extends View 
{
	/******************************************************
	 *************** @category Variables ******************
	 *****************************************************/
	
	/******************************************************
	 * Log
	 */
	private static final String TAG = "SpinningDrawableView";
	private static final boolean LOG = false;
	
	/*****************************************************
	 * Rotation
	 */
	public static final float MAX_ROTATION_DEGREES = 60.0f;
	public static final float MIN_ROTATION_DEGREES = 0.0f;
	
	private static final float DEGREES_PER_PERIOD = 360.0f;
	private static final float DEGREES_PER_QUARTER_PERIOD = DEGREES_PER_PERIOD / 4.0f;
	private static final float DEGREES_PER_HALF_PERIOD = DEGREES_PER_PERIOD / 2.0f;
	private static final float DEGREES_PER_THREE_QUARTER_PERIOD = (DEGREES_PER_PERIOD * 3.0f) / 4.0f;
	
	private float rotationDegrees = 0;
	private float rotationStepDegrees;
	

	private float rotationPivotXCoefficient = 0.5f;	//Middle by default
	private float rotationPivotYCoefficient = 0.5f;	//Middle by default
	
	private int rotationPivotX;
	private int rotationPivotY;
	
	private boolean rotating = false;
	
	/****************************************************
	 * Friction
	 */
	private static final float FRICTION = 0.5f;
	
	/****************************************************
	 * Obstacle
	 */
	private static final float BOUNCE_ENERGY_COEFFICIENT = 0.2f;
	
	private boolean obstacleExists = false;
	
	/****************************************************
	 * GUI
	 */
	private int drawableId = -1;
	private BitmapDrawable drawable = null;
	private static Matrix matrix = new Matrix();
	
	/******************************************************
	 * ???
	 */

	private static final int TOUCH_NOT 		= 0;
	private static final int TOUCH_TOP  	= 1;
	private static final int TOUCH_BOTTOM 	= 2;
	
	private int touchState;
	
	private float formerAngle = 0.0f;
	private long formerTime = -1L;
	private float succeedingAngle = 0.0f;
	private long succeedingTime = -1L;
	

	private static final float ARC_OF_TOLERANCE = 30.0f; //in degrees
	
	private static final float VELOCITY_MAX = 1.0f;
	
	private float angle1;
	private float angle2;
	private float angle3;
	
	private OnStartRotatingListener onStartRotatingListener;
	private OnStopRotatingListener onStopRotatingListener;

	/******************************************************
	 ***************** @category Constructors *************
	 *****************************************************/
	
	public SpinningDrawableView(Context context) 
	{
		super(context);
		
        setOnTouchListener(new SwipeDetector());
	}
	
	public SpinningDrawableView(Context context, AttributeSet attrs) 
	{
        this(context, attrs, 0);
        
        setOnTouchListener(new SwipeDetector());
    }
    
    public SpinningDrawableView(Context context, AttributeSet attrs, int defStyle) 
    {
        super(context, attrs, defStyle);
        
        //Initilize view according to pre-hints provided via xml
        init(attrs);
        
        setOnTouchListener(new SwipeDetector());
    }
    
    /******************************************************
	 ***************** @category Methods ******************
	 *****************************************************/
    
	/* (non-Javadoc)
	 * @see android.view.View#onMeasure(int, int)
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) 
	{
		//setMeasuredDimension(drawable.getBitmap().getWidth(), drawable.getBitmap().getHeight());
		
		/*
		 * TODO check wrap content datas
		 */
		
		int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);

		//Square by nature
		width = height = Math.min(width, height);

		setMeasuredDimension(width, height);

		if((drawableId != -1) && (drawable == null))
		{
			setResourceDrawable(drawableId);
		}
	}
	
	private void init(AttributeSet attrs) 
	{
		TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.spinthebottle);

		//Fetch resource drawable id if defined
		drawableId = a.getResourceId(R.styleable.spinthebottle_bottle_drawable, -1);

		//Remove artifacts
		a.recycle();
	}
	
	private DisplayMetrics initializeMetrics() 
	{
		// Define metrics for hardware configuration
		DisplayMetrics metrics = new DisplayMetrics();
		((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(metrics);

		return metrics;
	}

	/* (non-Javadoc)
	 * @see android.view.View#onDraw(android.graphics.Canvas)
	 */
	protected void onDraw(Canvas canvas) 
	{
		if(drawable != null)
		{
			synchronized (this) 
			{
				updateRotationDegree();
			}
			
			matrix.reset();
			
			matrix.setRotate(rotationDegrees, rotationPivotX, rotationPivotY);
			
			canvas.drawBitmap(drawable.getBitmap(), matrix, null);
			
			if(rotating)
			{
				invalidate();
			}
		}
	}

	private void updateRotationDegree()
	{
		if(obstacleExists)
		{
			if(rotationStepDegrees > 0)
			{//If rotating to clockwise
				
				//Top
				angle1 = Float.valueOf((rotationDegrees + ARC_OF_TOLERANCE) % DEGREES_PER_PERIOD);
				//Obstacle
				angle2 = Float.valueOf(succeedingAngle);
				//Bottom
				angle3 = Float.valueOf((rotationDegrees + ARC_OF_TOLERANCE + DEGREES_PER_HALF_PERIOD) % DEGREES_PER_PERIOD);
				
				if(angle2 < DEGREES_PER_QUARTER_PERIOD)
				{
					//If shifting required,
					if((angle1 >= DEGREES_PER_THREE_QUARTER_PERIOD) || (angle3 >= DEGREES_PER_THREE_QUARTER_PERIOD))
					{
						//shift each angle by Period/4
						angle1 += DEGREES_PER_QUARTER_PERIOD;
						angle1 %= DEGREES_PER_PERIOD;
						
						angle3 += DEGREES_PER_QUARTER_PERIOD;
						angle3 %= DEGREES_PER_PERIOD;
					
						angle2 += DEGREES_PER_QUARTER_PERIOD;
					}
				}
				
				if((angle2 > angle1) && (angle2 < (angle1 + rotationStepDegrees)))
				{//If within the range of succeeding step					
					//Make bottle hit the obstacle
					rotationDegrees = succeedingAngle - ARC_OF_TOLERANCE;
					rotationDegrees += (rotationDegrees < 0.0f)?(DEGREES_PER_PERIOD):(0.0f);
					rotationStepDegrees = -(rotationStepDegrees * BOUNCE_ENERGY_COEFFICIENT);				
				}
				else if((angle2 > angle3) && (angle2 < (angle3 + rotationStepDegrees)))
				{//If within the range of succeeding step
					//Make bottle hit the obstacle
					rotationDegrees = (succeedingAngle - ARC_OF_TOLERANCE + DEGREES_PER_HALF_PERIOD) % DEGREES_PER_PERIOD;
					rotationStepDegrees = -(rotationStepDegrees * BOUNCE_ENERGY_COEFFICIENT);				
				}
				else
				{
					//Succeed rotation degrees
					rotationDegrees += rotationStepDegrees;
					//putRotation around unit circle
					if(rotationDegrees < 0)
					{
						rotationDegrees += DEGREES_PER_PERIOD;
					}
					else
					{
						rotationDegrees %= DEGREES_PER_PERIOD;
					}
				
					applyFriction();
				}
			}
			else if(rotationStepDegrees < 0)
			{//If rotating to counter-clockwise
				
				//Top
				angle1 = Float.valueOf(rotationDegrees - ARC_OF_TOLERANCE);
				angle1 += (angle1 < 0.0f)?(DEGREES_PER_PERIOD):(0.0f);
				//Obstacle
				angle2 = Float.valueOf(succeedingAngle);
				//Bottom
				angle3 = Float.valueOf((rotationDegrees - ARC_OF_TOLERANCE + DEGREES_PER_HALF_PERIOD) % DEGREES_PER_PERIOD);
				
				if(angle2 > DEGREES_PER_THREE_QUARTER_PERIOD)
				{
					//If shifting required,
					if((angle1 < DEGREES_PER_QUARTER_PERIOD) || (angle3 < DEGREES_PER_QUARTER_PERIOD))
					{
						//shift each angle by Period/4
						angle2 += DEGREES_PER_QUARTER_PERIOD;
						angle2 %= DEGREES_PER_PERIOD;
					
						angle1 += DEGREES_PER_QUARTER_PERIOD;
						angle3 += DEGREES_PER_QUARTER_PERIOD;
					}
				}
				
				if((angle2 < angle1) && (angle2 > (angle1 + rotationStepDegrees)))
				{//If within the range of succeeding step
					//Make bottle hit the obstacle
					rotationDegrees = (succeedingAngle + ARC_OF_TOLERANCE) % DEGREES_PER_PERIOD;
					rotationStepDegrees = -(rotationStepDegrees * BOUNCE_ENERGY_COEFFICIENT);				
				}
				else if((angle2 < angle3) && (angle2 > (angle3 + rotationStepDegrees)))
				{//If within the range of succeeding step
					//Make bottle hit the obstacle
					rotationDegrees = (succeedingAngle + ARC_OF_TOLERANCE + DEGREES_PER_HALF_PERIOD) % DEGREES_PER_PERIOD;
					rotationStepDegrees = -(rotationStepDegrees * BOUNCE_ENERGY_COEFFICIENT);				
				}
				else
				{
					//Succeed rotation degrees
					rotationDegrees += rotationStepDegrees;
					//putRotation around unit circle
					if(rotationDegrees < 0)
					{
						rotationDegrees += DEGREES_PER_PERIOD;
					}
					else
					{
						rotationDegrees %= DEGREES_PER_PERIOD;
					}
				
					applyFriction();
				}
			}
		}
		else
		{
			//Succeed rotation degrees
			rotationDegrees += rotationStepDegrees;
			//putRotation around unit circle
			if(rotationDegrees < 0)
			{
				rotationDegrees += DEGREES_PER_PERIOD;
			}
			else
			{
				rotationDegrees %= DEGREES_PER_PERIOD;
			}
					
			applyFriction();
		}
	}
	
	/**
	 * <p>
	 * Simulates the friction effect applied by gravity and surface to the bottle
	 * </p>
	 * <p>
	 * Decelerate rotation speed by RotateView2.FRICTION at each call
	 * </p>
	 */
	private void applyFriction() 
	{
		if(MIN_ROTATION_DEGREES == Math.round(rotationStepDegrees))
		{
			stopRotating();
		}
		else
		{
			//Apply friction
			rotationStepDegrees += (rotationStepDegrees < 0)?FRICTION:-FRICTION;
		}
	}

	/**
	 * Update bitmap object with new bitmap.
	 * 
	 * @param bitmap new bitmap data to spin
	 * @param optimizeBitmap boolean flag indicating, if bitmap should be resized for better memory management or not. Set to true if you have no idea what you are doing :).
	 */
	public void setBitmap(Bitmap bitmap, boolean optimizeBitmap)
	{
		setDrawable(new BitmapDrawable(bitmap), optimizeBitmap);
	}
	
	/**
	 * Updates drawable reference of the view.
	 * 
	 * @param drawable new drawable source
	 * @param optimizeDrawable boolean flag indicating, if drawable should be resized for better memory management or not. Set to true if you have no idea what you are doing :)
	 */
	public void setDrawable(BitmapDrawable drawable, boolean optimizeDrawable)
	{
		//Break bonds with old drawable
		if(this.drawable != null)
		{
			if(this.drawable.getBitmap() != null)
			{
				this.drawable.getBitmap().recycle();
			}
			
			this.drawable.setCallback(null);
			unscheduleDrawable(this.drawable);
			this.drawable = null;
			
			//Clear the orphan artifacts of ancient bitmap object
			System.gc();
		}
		
		//Must optimize new drawable's bitmap, in order to maintain peace at memory ^^^
		//get new drawable's bitmap instance
		Bitmap originalBitmap = drawable.getBitmap();
		
		//If drawable is not empty inside
		if(originalBitmap != null)
		{
			int targetWidth = getMeasuredWidth();
			int targetHeight = getMeasuredHeight();
			
			//Check if any optimization necessary
			if( optimizeDrawable && !((originalBitmap.getWidth() == targetWidth) && (originalBitmap.getHeight() == targetHeight)) )
			{//Do the optimization
				Bitmap optimizedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, targetWidth, targetHeight);
				//Create optimized drawable and update its bitmap, then update SpinningDrawableView.drawable.
				this.drawable = new BitmapDrawable(optimizedBitmap);
				this.drawable.setCallback(this);
				
				//Break bonds with non-optimized drawable
				originalBitmap.recycle();
				drawable.setCallback(null);
				unscheduleDrawable(drawable);
				drawable = null;
				
				//Clear the orphan artifacts of ancient bitmap object
				System.gc();
			}
			else
			{//No need to optimization
				//Just update the reference
				this.drawable = drawable;
				this.drawable.setCallback(this);
			}
			
			//Update rotation data
			rotationPivotX = (int) (drawable.getBitmap().getWidth() * rotationPivotXCoefficient);
			rotationPivotY = (int) (drawable.getBitmap().getHeight() * rotationPivotYCoefficient);
		}
		
		invalidate();
	}
	
	/**
	 * Initializes drawable bitmap from resources. Unlike setDrawable( Drawable ), 
	 * It optimizes bitmap on the first run. Thus a better memory management :P
	 * 
	 * @param resourceId
	 */
	public void setResourceDrawable(int resourceId)
	{
		drawableId = resourceId;
		
		//Initialize options
    	BitmapFactory.Options options = new BitmapFactory.Options();
    	
    	int imageDimension = getImageDimension(resourceId);
    	
    	DisplayMetrics metrics = initializeMetrics();
    	
    	//Modify options
    	options.inJustDecodeBounds = false;
    	options.inSampleSize = 1;//calculateSampleSize(metrics.widthPixels);
    	options.inScaled = true;
    	options.inTargetDensity = (int) (metrics.densityDpi * calculateFrameAndDrawableRatioCoefficient(getMeasuredWidth(), imageDimension));
    	options.inPurgeable = false;
    	options.inDensity = metrics.densityDpi;
    	
    	// Create bitmap with drawableId using options
    	Bitmap bitmap = BitmapFactory.decodeResource(getResources(), drawableId, options);
    	
    	//Update view's bitmap object without optimization as we done it above.
    	setBitmap(bitmap, false);
	}
	
	private int getImageDimension(int resourceId)
    {
		BitmapFactory.Options options = new BitmapFactory.Options();
		
		//set decode only bounds
		options.inJustDecodeBounds = true;
		
		BitmapFactory.decodeResource(getResources(), resourceId, options);
		
		return (Math.max(options.outWidth, options.outHeight));
    }
	
	private float calculateFrameAndDrawableRatioCoefficient(int preferredDimension, int drawableDimension)
    {
		return ((float) preferredDimension / (float) drawableDimension);
    }
	
	public void setRotationStepDegrees(float degrees)
	{
		this.rotationStepDegrees = degrees;
	}
	
	private float getRotationStepDegrees()
	{
		return this.rotationStepDegrees;
	}
	
	public void rotateTo(float degrees)
	{
		rotationDegrees = degrees;
		rotationStepDegrees = 0.0f;
		
		invalidate();
	}
	
	public void startRotating()
	{
		rotating = true;
		
		if(onStartRotatingListener != null)
		{
			onStartRotatingListener.onStart(getRotationStepDegrees());
		}
		
		invalidate();
	}
	
	public void stopRotating()
	{
		if(rotating && (onStopRotatingListener != null))
		{
			onStopRotatingListener.onStop(rotationDegrees);
		}
		
		rotating = false;
	}
	
	private float calculateAngularVelocity()
	{
		float velocity = 0.0f;
		
		if(formerTime != -1L)
		{
			float angularDistance = succeedingAngle - formerAngle;
			
			//Fix for circular property of angle
			if(angularDistance < -180)
			{
				angularDistance += DEGREES_PER_PERIOD;
			}
			//Again another fix for circular property;
			else if(angularDistance > 180)
			{
				angularDistance = Math.abs(angularDistance - DEGREES_PER_PERIOD);
			}
			
			float elapsedTime = (float) (succeedingTime - formerTime);
			
			velocity = angularDistance / elapsedTime;
		}
		
		return velocity;
	}
	
	public void setOnStartRotatingListener(OnStartRotatingListener listener)
	{
		this.onStartRotatingListener = listener;
	}
	
	public void setOnStopRotatingListener(OnStopRotatingListener listener)
	{
		this.onStopRotatingListener = listener;
	}
	
	/**
	 * Sets initial rotation degree. Set a value between
	 * SpinningDrawableView.MAX_ROTATION_DEGREES and SpinningDrawableView.MIN_ROTATION_DEGREES
	 * 
	 * @param rotationSpeed Angular rotation speed
	 */
	public void setRotationSpeed(float rotationSpeed) 
	{
		this.rotationStepDegrees = rotationSpeed;
	}

	/**
	 * 
	 * @return Image rotation angle
	 */
	public float getRotationDegree() 
	{
		return this.rotationDegrees;
	}
	
	/******************************************************
	 *************** @category Subclasses******************
	 *****************************************************/
	
	class SwipeDetector implements View.OnTouchListener
	{
		public boolean onTouch(View v, MotionEvent event) 
		{
			switch(event.getAction()) 
			{
				case MotionEvent.ACTION_DOWN:
				{
					touchState = isInTouchWithObject(event);
					
					switch(touchState)
					{
						case TOUCH_TOP:
						{
							stopRotating();
							
							float x =  (event.getX() - (getWidth()  / 2));
							float y = -(event.getY() - (getHeight() / 2));
							
							succeedingAngle = -((float) Math.toDegrees(Math.atan2(y,x))) + 90;
							succeedingAngle += (succeedingAngle < 0)?(DEGREES_PER_PERIOD):(0);
							succeedingTime = System.currentTimeMillis();
							
							rotateTo(succeedingAngle);
							
							break;
						}
						case TOUCH_BOTTOM:
						{
							stopRotating();
							
							float x =  (event.getX() - (getWidth()  / 2));
							float y = -(event.getY() - (getHeight() / 2));
							
							succeedingAngle = -((float) Math.toDegrees(Math.atan2(y,x))) - 90;
							succeedingAngle += (succeedingAngle < 0)?(DEGREES_PER_PERIOD):(0);
							succeedingTime = System.currentTimeMillis();
							
							rotateTo(succeedingAngle);
							
							break;
						}
						case TOUCH_NOT:
						{
							float x =  (event.getX() - (getWidth()  / 2));
							float y = -(event.getY() - (getHeight() / 2));
							
							succeedingAngle = -((float) Math.toDegrees(Math.atan2(y,x))) + 90;
							succeedingAngle += (succeedingAngle < 0)?(DEGREES_PER_PERIOD):(0);
							succeedingTime = System.currentTimeMillis();
							
							obstacleExists = true;
							
							break;
						}
					}
					
					break;
				}
				case MotionEvent.ACTION_MOVE:
				{
					switch(touchState) 
					{
						case TOUCH_TOP:
						{
							formerAngle = succeedingAngle;
							formerTime = succeedingTime;
							
							float x =  (event.getX() - (getWidth()  / 2));
							float y = -(event.getY() - (getHeight() / 2));
							
							succeedingAngle = -((float) Math.toDegrees(Math.atan2(y,x))) + 90;
							succeedingAngle += (succeedingAngle < 0)?(DEGREES_PER_PERIOD):(0);
							succeedingTime = System.currentTimeMillis();
							
							rotateTo(succeedingAngle);
							
							break;
						}
						case TOUCH_BOTTOM:
						{
							formerAngle = succeedingAngle;
							formerTime = succeedingTime;
							
							float x =  (event.getX() - (getWidth()  / 2));
							float y = -(event.getY() - (getHeight() / 2));
							
							succeedingAngle = -((float) Math.toDegrees(Math.atan2(y,x))) - 90;
							succeedingAngle += (succeedingAngle < 0)?(DEGREES_PER_PERIOD):(0);
							succeedingTime = System.currentTimeMillis();
							
							rotateTo(succeedingAngle);
							
							break;
						}
						case TOUCH_NOT:
						{
							formerAngle = succeedingAngle;
							formerTime = succeedingTime;
							
							float x =  (event.getX() - (getWidth()  / 2));
							float y = -(event.getY() - (getHeight() / 2));
							
							succeedingAngle = -((float) Math.toDegrees(Math.atan2(y,x))) + 90;
							succeedingAngle += (succeedingAngle < 0)?(DEGREES_PER_PERIOD):(0);
							succeedingTime = System.currentTimeMillis();
							
							obstacleExists = true;
							
							break;
						}
					}
					
					break;
				}
				case MotionEvent.ACTION_UP:
				{
					//If placed an obstacle before remove it.
					if(obstacleExists)
					{
						obstacleExists = false;
					}
					
					//If object tossed
					if((touchState != TOUCH_NOT) && (formerTime != -1L))
					{
						float velocity = calculateAngularVelocity();
					    
					    if(velocity > VELOCITY_MAX)
					    {//Rotate with maximum speed
					    	setRotationStepDegrees(MAX_ROTATION_DEGREES);
					    }
					    else if(velocity < -VELOCITY_MAX)
					    {//Rotate with maximum speed
					    	setRotationStepDegrees(-MAX_ROTATION_DEGREES);
					    }
					    else
					    {
					    	setRotationStepDegrees(MAX_ROTATION_DEGREES * velocity);
					    }
					    
				    	startRotating();
					}
					
					//reset artifacts
					formerTime = -1L;
					succeedingTime = -1L;
					
					break;
				}
				default:
				{
					return false;
				}
			}
			
			return true;
		}

		private int isInTouchWithObject(MotionEvent event) 
		{
			float x =  (event.getX() - (getWidth()  / 2));
			float y = -(event.getY() - (getHeight() / 2));
			
			float degrees = -((float) Math.toDegrees(Math.atan2(y,x))) + 90;
			
			float currentObjectAngle = (rotationDegrees < 0)?(rotationDegrees + DEGREES_PER_PERIOD):rotationDegrees;
			
			if(Math.abs(currentObjectAngle - degrees) < ARC_OF_TOLERANCE)
			{
				return TOUCH_TOP;
			}
			else if(Math.abs(currentObjectAngle - degrees - DEGREES_PER_PERIOD) < ARC_OF_TOLERANCE)
			{
				return TOUCH_TOP;
			}
			else if(Math.abs((Math.abs(currentObjectAngle - degrees) - 180)) < ARC_OF_TOLERANCE)
			{
				return TOUCH_BOTTOM;
			}
			
			return TOUCH_NOT;
		}
	}
	
	public interface OnStopRotatingListener
	{
		public abstract void onStop(float stopAngle);
	}
	
	public interface OnStartRotatingListener
	{
		public abstract void onStart(float angularVelocity);
	}
}
