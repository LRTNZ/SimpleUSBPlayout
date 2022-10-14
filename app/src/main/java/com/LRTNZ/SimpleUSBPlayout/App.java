package com.LRTNZ.SimpleUSBPlayout;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Timer;
import java.util.TimerTask;
import org.jetbrains.annotations.NotNull;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;
import timber.log.Timber;

/**
 * Main Activity of the application
 */
public class App extends Activity implements IVLCVout.Callback{


  /**
   * {@link LibVLC} instance variable, to be used as required
   */
  LibVLC libVLC = null;

  /**
   * {@link org.videolan.libvlc.MediaPlayer} instance, used to play the media in the app
   */
  org.videolan.libvlc.MediaPlayer mediaPlayer = null;

  /**
   * {@link Media} source instance, provides the source for the {@link #mediaPlayer} instance
   */
  static Media mediaSource;


  /**
   * {@link IVLCVout} instance to be used in the app
   */
  IVLCVout vlcOut;

  // Surfaces for the stream to be displayed on
  SurfaceHolder vidHolder;
  SurfaceView vidSurface;


  /**
   * {@link Integer} value of which of the two streams is being played
   */
  // Actual first stream to be played is the inverse of this, just the quick logic setup I put together needs it this way
  int currentStreamIndex = 1;


  /**
   * {@link String} value of the network address of the current streaming source to be played back
   */
  String currentStreamAddress = "";

  // Text box at the top of the screen, that will have the current stream name/index being played in it, to make it easy to see what is happening in the application

  /**
   * {@link EditText} that is the box at the top of the screen showing the details about the current stream being played
   */
  EditText streamName;


  /**
   * {@link ArrayList}<{@link String}> of the two IP addresses of the multicast streams that are to be cycled through.
   * These are where you load in the addressed of the two multicast streams you are creating on your own network, to run this application.
   */

  // |---------------------------|
  // | Configure stream IPs here |
  // |---------------------------|

  ArrayList<String> streamAddresses = new ArrayList<String>(){{
    add("udp://@239.2.2.2:1234");
    add("udp://@239.1.1.1:1234");
  }};

  ArrayList<String> videoFiles = new ArrayList<String>();//{{
  //  add("resort_flyover.mp4");
   // add("waves_crashing.mp4");
   // add("happy-test.jpg");
  //  add("Test-Logo.png");
 // }};


  static boolean streamOrFile = false;

  static int numPlaybacks = 0;

  @Override
  protected void onCreate(Bundle savedInstance){

    Toast toast = Toast.makeText(this, "Called on create", Toast.LENGTH_SHORT);
    toast.show();

    // Run the super stuff for this method
    super.onCreate(savedInstance);

    // Creates the timber debug output, and sets the tag for the log messages
    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree() {
        @Override
        protected void log(int priority, String tag, @NotNull String message, Throwable t) {
          super.log(priority, "Test-VLC", message, t);
        }
      });
      Timber.d("In debug mode");
    }


    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    // Sets the main view
    setContentView(R.layout.main);

    // Populates and loads the two values for the video layout stuff
    vidSurface = findViewById(R.id.video_layout);
    vidSurface.setVisibility(View.VISIBLE);
    vidHolder = vidSurface.getHolder();


    File baseFilePath;
    if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
      File tempPath = new File("/mnt/usb/");
      baseFilePath = tempPath.listFiles()[0];

      Timber.d(baseFilePath.toString());
      File[] usbFiles = baseFilePath.listFiles();
      Timber.d(String.valueOf(usbFiles.length));
      for (int i = 0; i < usbFiles.length; i++) {
        if (usbFiles[i].isFile()) {
          Timber.d("File " + usbFiles[i].getName());
        } else if (usbFiles[i].isDirectory()) {
          Timber.d("Directory " + usbFiles[i].getName());
        }
      }


      //Timber.d(Environment.getExternalStorageDirectory().list().toString());
      if (usbFiles != null) {
        for(int i = 0; i <usbFiles.length; i++){

          // https://stackoverflow.com/questions/45796234/mimetypemap-fail-to-return-extension-for-a-filename-that-contains-spaces
          //https://stackoverflow.com/questions/5455794/removing-whitespace-from-strings-in-java
          String fileType = MimeTypeMap.getFileExtensionFromUrl(usbFiles[i].getAbsolutePath().replaceAll("\\s+", ""));
          Timber.d("File type: " + fileType);
          // Clever solution
          ///https://stackoverflow.com/questions/7604814/best-way-to-format-multiple-or-conditions-in-an-if-statement
          if(Arrays.asList("mp4", "jpeg", "png").contains(fileType)){
            videoFiles.add(usbFiles[i].getAbsolutePath());
          }
        }
      } else {
        toast = Toast.makeText(this, "Failed to read usb", Toast.LENGTH_SHORT);
        toast.show();
      }
    }


    // Adds arguments to the list of args to pass when initialising the lib VLC instance.
    // If you need to add in more arguments to the vlc instance, just follow the format below

    // |-----------------------------|
    // | Additional LibVLC Arguments |
    // |-----------------------------|

    addArg("fullscreen", "--fullscreen");
    addArg("decode", ":codec=mediacodec_ndk,mediacodec_jni,none");
    addArg("verbose", "-v");

  //  addArg("deinterlace", "--deinterlace=1");
    //addArg("mode","--deinterlace-mode=yadif");
   // addArg("filter","--video-filter=deinterlace");

    // Load the editText variable with a reference to what it needs to fill in the layout
   // streamName = findViewById(R.id.stream_ID);

    // Run the libVLC creation/init method
    createLibVLC();
  }

  /**
   * Method that handles the creation of the {@link LibVLC} instance
   */
  public void createLibVLC() {

    // Get the list of arguments from the provided arguments above
    ArrayList<String> args = new ArrayList<>(arguments.values());

    // Debug: Print out the passed in arguments
    Timber.d("Arguments for VLC: %s", args);

    // Create the LibVLC instance, with the provided arguments
    libVLC = new LibVLC(this, args);

    // Create the new media player instance to be used
    mediaPlayer = new org.videolan.libvlc.MediaPlayer(libVLC);

    // Get the details of the display
    DisplayMetrics displayMetrics = new DisplayMetrics();

    // Load displayMetrics with the details of the default display of the device
    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

    // Set the size of the mediaplayer to match the resolution of the device's screen
    mediaPlayer.getVLCVout().setWindowSize(displayMetrics.widthPixels, displayMetrics.heightPixels);

    // Load vlcOut with the value from the created media player
    vlcOut = mediaPlayer.getVLCVout();

    // Passes the event listener for the media player to use to be this runnable/lambda
    mediaPlayer.setEventListener(event -> {

      // Standard switch between all the different events thrown by the mediaplayer
      switch (event.type) {
        case MediaPlayer.Event.Buffering:
         // Timber.d("onEvent: Buffering");
          break;
        case MediaPlayer.Event.EncounteredError:
          Timber.d("onEvent: EncounteredError");
          break;
        case MediaPlayer.Event.EndReached:
          //Timber.d("onEvent: EndReached");
          break;
        case MediaPlayer.Event.ESAdded:
         // Timber.d("onEvent: ESAdded");
          break;
        case MediaPlayer.Event.ESDeleted:
         // Timber.d("onEvent: ESDeleted");
          break;
        case MediaPlayer.Event.MediaChanged:
          Timber.d("onEvent: MediaChanged");
          //mediaPlayer.setVolume(0);
          break;
        case MediaPlayer.Event.Opening:
          Timber.d("onEvent: Opening");
          break;
        case MediaPlayer.Event.PausableChanged:
         // Timber.d("onEvent: PausableChanged");
          break;
        case MediaPlayer.Event.Paused:
        //  Timber.d("onEvent: Paused");
          break;
        case MediaPlayer.Event.Playing:
          Timber.d("onEvent: Playing");
          break;
        case MediaPlayer.Event.PositionChanged:
          //  Timber.d("onEvent: PositionChanged");
          break;
        case MediaPlayer.Event.SeekableChanged:
         // Timber.d("onEvent: SeekableChanged");
          break;
        case MediaPlayer.Event.Stopped:
          Timber.d("onEvent: Stopped");
          changeStream();
          break;
        case MediaPlayer.Event.TimeChanged:
          //  Timber.d("onEvent: TimeChanged");
          break;
        case MediaPlayer.Event.Vout:
         // Timber.d("onEvent: Vout");
          break;
      }
    });

    // Call the change stream, to preload the first stream at startup, instead of waiting for an input
    changeStream();

    // If you do not have the means to automatically generate an alternative two pulse up/two pulse down signal input for the Android TV,
    // these two lines can be uncommented in order to enable the automatic up/down changing.
    // The reason there are the two input options, is to prove it is not the source of the call to changing the stream that is causing the issues with the crashing.

    // |------------------------------------|
    // | Optional automatic stream changing |
    // |------------------------------------|

     runAutomaticTimer = true;
     //runTimedStreamChange();
  }


  /**
   * {@link LinkedHashMap}<{@link String}, {@link String}> of the arguments that are to be passed to the LibVLC instance
   */
  static LinkedHashMap<String, String> arguments = new LinkedHashMap<>();

  /**
   * Method that takes a k/v pair and adds it to the map of arguments to be used when creating the LibVLC instance.
   * The key is used in the full application, as the potential to remove existing arguments is present there.
   *
   * @param argName {@link String} value of the name to use as the key for the argument
   * @param argValue {@link String} value of the argument that will be recognised when passed to LibVLC
   */
  public void addArg(String argName, String argValue) {

    // If the argument with the key already exists, just update the existing one to the new value
    if (arguments.containsKey(argName)) {
      arguments.replace(argName, argValue);
    } else {
      // Otherwise if the argument does not exist, add it as a new one to the list
      arguments.put(argName, argValue);
    }
  }


  @Override
  public void onNewIntent(Intent intent) {

    // Standard android stuff
    super.onNewIntent(intent);
    Timber.d("Player ran new intent");

    setIntent(intent);
  }

  @Override
  public void onStart() {

    // Run super stuff
    super.onStart();

    // Set the output view to use for the video to be the surface
    vlcOut.setVideoView(vidSurface);

    // Add the callback for the vlcOut to be this class
    mediaPlayer.getVLCVout().addCallback(this);
    // Attach the video views passed to the output
    vlcOut.attachViews();

  }

  @Override
  public void onResume() {
    super.onResume();
    Timber.d("App ran resume");
  }

  @Override
  public void onPause() {
    super.onPause();

    runAutomaticTimer = false;

    //vlcOut.removeCallback(this);
    Timber.d("App ran paused");
  }

  @Override
  public void onStop() {
    super.onStop();
    mediaPlayer.stop();
    vlcOut.detachViews();
    // Release the various VLC things when the activity is stopped
//    mediaPlayer.stop();
    //   runAutomaticTimer = false;
    //   mediaPlayer.getVLCVout().detachViews();
    //   mediaPlayer.getVLCVout().removeCallback(this);
    //   mediaPlayer.release();

    Timber.d("Player ran stop");
  }

  @Override
  public void onDestroy(){
    Timber.d("Player destroyed");
    super.onDestroy();
    mediaPlayer.release();
    libVLC.release();
  }

  /**
   * {@link Boolean} value that stores whether or not the automatic timer should cancel, once it has been set going
   */
  volatile boolean runAutomaticTimer = false;

  /**
   * Method that can be called to start a timer to automatically change the stream every 10 seconds from inside the application.
   */
 // void runTimedStreamChange(){

    //Timer timer = new Timer();
   // timer.scheduleAtFixedRate(new TimerTask() {
     // @Override
     // public void run() {
     //   if(!runAutomaticTimer){
      //    this.cancel();
      //  }
      //  runOnUiThread(() -> changeStream());
      //}
  //  }, 5000, 15000);
  //}


  /**
   * Method that is called to change the multicast stream VLC is currently playing
   */
  void changeStream(){

    if(currentStreamIndex < videoFiles.size()){
      currentStreamAddress = videoFiles.get(currentStreamIndex);
      currentStreamIndex ++;
    } else {
      currentStreamIndex = 0;
      currentStreamAddress = videoFiles.get(currentStreamIndex);
    }
   // Timber.d("Selected File: 0 - %s", videoFiles.get(currentStreamIndex));
    Timber.d(currentStreamAddress);

    // Load the values of the current stream and index into the textbox at the top of the screen, to make it easier to see what is happening
  //  streamName.setText(String.format("Stream: %s/%s", currentStreamIndex,currentStreamAddress));

    // If the current media source is not null, as it would be at start up, release it.
    if (mediaSource != null) {
      mediaSource.release();
    }

    if(streamOrFile){
      mediaSource = new Media(this.libVLC, Uri.parse(this.currentStreamAddress));
    } else {
     // try {
      //  FileDescriptor fd;
     //   fd = getContentResolver().openFileDescriptor(Uri.parse("file:///" + this.currentStreamAddress), "r").getFileDescriptor();
    //    mediaSource = new Media(this.libVLC, fd);
        mediaSource = new Media(this.libVLC, Uri.parse("file:///" + this.currentStreamAddress));
        mediaSource.setHWDecoderEnabled(true, false);

    }


    // Finish up the process of loading the stream into the player
    finishPlayer();
  }

  /**
   * Method that is called to load in a new mediasource and to set it playing out the output, from VLC
   */
  void finishPlayer(){

    if(mediaPlayer.isPlaying()){
      mediaPlayer.stop();
    }

    // Add the option to be in fullscreen to the new mediasource
    mediaSource.addOption(":fullscreen");

   // mediaPlayer.
    // Set the player to use the provided media source
    mediaPlayer.setMedia(mediaSource);

    // Release the media source
    mediaSource.release();

    // Start the media player
    mediaPlayer.play();

    Timber.d("Number of playbacks: %s", numPlaybacks);
    numPlaybacks ++;
  }

  // Required handler things for the vlcOut interface

  @Override
  public void onSurfacesCreated(IVLCVout ivlcVout) {

  }

  @Override
  public void onSurfacesDestroyed(IVLCVout ivlcVout) {

  }



}