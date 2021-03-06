package com.example.recipeapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatRatingBar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class RecipeActivity extends AppCompatActivity {

    private static final String TAG = "RecipeActivity";
    
    private TextView title, ready_in, servings, instructions, aggregateLikes;
    private Long spoonacularAggregateLikes = 0L;
    private Long recipeAppLikes = 0L;
    private ImageView img;
    private DatabaseReference mRootRef;
    private DatabaseReference mLikesRef;
    private DatabaseReference mRecipeLikesCountRef;
    private DatabaseReference mRecipeAverageDifficultyRatingRef;
    private FirebaseAuth mAuth;
    private String recipe_sourceUrl;
    private AnalysedInstructions analysedInstructions;
    private RecyclerView ingredients_rv;
    private RecyclerView instructions_rv;
    private FloatingActionButton fab_bookmark;
    private FloatingActionButton fab_useontab;
    private FloatingActionButton fab_sendtowatch;
    private EditText timeEt;
    private Button startBtn;
    private boolean started;
    private CountDownTimer countDownTimer;
    private AppCompatRatingBar difficultyRatingBar;

    private boolean thumbsLike = false;
    private FloatingActionButton fabThumbsLike;

    private boolean bookmarkLike = false;
    private int RecipeAlarmTime;
    private int RecipeID;
    private static final Handler HANDLER = new Handler();

    AlarmManager myAlarmManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe);

        Log.v(TAG, " : OnCreate - starting");
        myAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        final Intent intent = getIntent();
        final String recipeId = Objects.requireNonNull(intent.getExtras()).getString("id");
        mAuth = FirebaseAuth.getInstance();
        final String uid = mAuth.getCurrentUser().getUid();
        mRootRef = FirebaseDatabase.getInstance().getReference().child(uid).child(recipeId);
        mLikesRef = FirebaseDatabase.getInstance().getReference().child("profiles").child(uid).child("likes").child(recipeId);
        mRecipeLikesCountRef = FirebaseDatabase.getInstance().getReference().child("recipes").child(recipeId).child("likes");
        mRecipeAverageDifficultyRatingRef = FirebaseDatabase.getInstance().getReference().child("recipes").child(recipeId).child("difficulty_ratings").child("average");
        img = findViewById(R.id.recipe_img);
        title = findViewById(R.id.recipe_title);
        ready_in = findViewById(R.id.recipe_ready_in);
        servings = findViewById(R.id.recipe_servings);
        aggregateLikes = findViewById(R.id.aggregate_likes);
        //vegeterian = findViewById(R.id.recipe_vegetarian);
        instructions = findViewById(R.id.recipe_instructions);
        fab_bookmark = findViewById(R.id.fab_bookmark);
        fab_useontab = findViewById(R.id.fab_useontab);
        fab_sendtowatch = findViewById(R.id.fab_sendtowatch);
        fabThumbsLike = findViewById(R.id.fab_like_button);
        difficultyRatingBar = findViewById(R.id.difficulty_rating_bar);


        timeEt = findViewById(R.id.timeEt);
        startBtn = findViewById(R.id.startBtn);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (started) {
                    started = false;
                    startBtn.setText(R.string.start);
                    timeEt.setCursorVisible(true);
                    countDownTimer.cancel(); // we cancel the countdown timer execution when user click on the stop button
                } else {
                    started = true;
                    startBtn.setText(R.string.stop);
                    timeEt.setCursorVisible(false);

                    // we get raw time entered by user at min:sec format
                    String rawTime = timeEt.getText().toString();
                    String[] tmp = rawTime.split(":");

                    long time = 60 * 1000; // default time = 60 sec.

                    // we try to parse the time entered by user
                    try {
                        time = (Integer.parseInt(tmp[0]) * 60 + Integer.parseInt(tmp[1])) * 1000;
                    } catch (Exception e) {
                        timeEt.setText(R.string.default_time); // default time is set if error !
                    }

                    countDownTimer = new CountDownTimer(time, 100) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            // we update the counter during the execution
                            long remainingSeconds = millisUntilFinished / 1000;
                            long min = remainingSeconds / 60;
                            long seconds = remainingSeconds % 60;

                            timeEt.setText(min + ":" + (seconds < 10 ? "0" + seconds : seconds));
                        }

                        @Override
                        public void onFinish() {
                            // execution is finished, we set default values
                            timeEt.setText(R.string.start_time);
                            startBtn.setText(R.string.start);

                            // we display a user message
                            new AlertDialog.Builder(RecipeActivity.this).
                                    setTitle(R.string.app_name).
                                    setMessage(R.string.lets_eat).
                                    show();

                            // we reset to default time in the future (1.5 seconds seem good)
                            HANDLER.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    timeEt.setText(R.string.default_time);
                                }
                            }, 1500);
                        }
                    };

                    countDownTimer.start();

                }
            }
        });

        Log.v(TAG, "OnCreate - try getRecipeInstructions");
        try {
            getRecipeInstructions(recipeId);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        Log.v(TAG, "OnCreate - add listeners to firebase");
        mRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.v(TAG, String.valueOf(dataSnapshot));
                if (dataSnapshot.getValue() != null) {
                    fab_bookmark.setImageResource(R.drawable.bookmarked);
                    bookmarkLike = true;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        Log.v(TAG, "OnCreate - add listeners to fab");
        fab_bookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bookmarkLike = !bookmarkLike;
                mRootRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (bookmarkLike) {
                            fab_bookmark.setImageResource(R.drawable.bookmarked);
                            Map favorites = new HashMap();
                            favorites.put("img", intent.getExtras().getString("img"));
                            favorites.put("title", intent.getExtras().getString("title"));
                            mRootRef.setValue(favorites);
                        } else {
                            try {
                                fab_bookmark.setImageResource(R.drawable.bookmark);
                                mRootRef.setValue(null);
                            } catch (Exception e) {
                            }
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });
            }
        });

        Log.v(TAG, "OnCreate - setting onclick to useontab fab");
        fab_useontab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "useontab - onClick");
                //startcountdown to alarm

                StartRecipeAlarm(v);

            }
        });


        fab_sendtowatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //send instructions to watch
                try {
                    getPackageManager().getPackageInfo("com.google.android.wearable.app", PackageManager.GET_META_DATA);

                    //Toast.makeText(getApplicationContext(), "The Android Wear App is installed", Toast.LENGTH_SHORT).show();
                } catch (PackageManager.NameNotFoundException e) {
                    //android wear app is not installed
                    Toast.makeText(getApplicationContext(), "The Android Wear App is NOT installed", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (Constants.Recipe_on_Watch==false){
                    sendRecipetoWatch(recipeId);
                    Toast.makeText(getApplicationContext(), "Instructions sent to watch!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Another recipe running on watch!", Toast.LENGTH_LONG).show();
                }
            }
        });

        mLikesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.v(TAG, String.valueOf(dataSnapshot));
                if (dataSnapshot.getValue() != null) {
                    fabThumbsLike.getBackground().setTint(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary));
                    fabThumbsLike.getDrawable().setTint(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));
                    thumbsLike = true;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        mRecipeAverageDifficultyRatingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long average = (Long) snapshot.getValue();
                if(average != null) {
                    difficultyRatingBar.setRating(average);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        mRecipeLikesCountRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long likesCount = (Long) snapshot.getValue();
                        if(likesCount == null){
                            likesCount = 0L;
                        }
                        recipeAppLikes = likesCount;
                        aggregateLikes.setText(getAddedLikesString());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });

        fabThumbsLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                thumbsLike = !thumbsLike;

                if(thumbsLike){
                    fabThumbsLike.getBackground().setTint(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary));
                    fabThumbsLike.getDrawable().setTint(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));
                }
                else{
                    fabThumbsLike.getBackground().setTint(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));
                    fabThumbsLike.getDrawable().setTint(ContextCompat.getColor(getApplicationContext(), R.color.common_google_signin_btn_text_light_focused));
                }

                mLikesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (thumbsLike) {
                            mLikesRef.setValue(true);
                        } else {
                            try {
                                mLikesRef.setValue(null);
                            } catch (Exception e) {
                            }
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });

                mRecipeLikesCountRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long likesCount = (Long) snapshot.getValue();
                        if(likesCount == null){
                            likesCount = 0L;
                        }
                        if(thumbsLike) {
                            likesCount += 1;
                        } else {
                            likesCount = Long.max(0L, likesCount - 1);
                        }
                        mRecipeLikesCountRef.setValue(likesCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
            }
        });


        Log.v(TAG, "OnCreate - set recycler view ingredient");
        ingredients_rv = findViewById(R.id.recipe_ingredients_rv);
        ingredients_rv.setLayoutManager(new GridLayoutManager(this, 2));

        Log.v(TAG, "OnCreate - set recycler view instructions");
        instructions_rv = findViewById(R.id.recipe_instructions_rv);
        instructions_rv.setLayoutManager(new LinearLayoutManager(this));
    }


    private void getRecipeInstructions(final String recipeId) throws IOException, JSONException {
        //https://api.spoonacular.com/recipes/informationBulk?ids=1&apiKey=e5f41960a96343569669c5435cdc2710
        String URL = " https://api.spoonacular.com/recipes/" + recipeId + "/information?addRecipeInformation=true&apiKey="+Constants.spoonacularApiKey;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(URL)
                .get()
                .addHeader("cache-control", "no-cache")
                .addHeader("postman-token", "0c62c820-d52c-fa96-eab0-b6829dc11b00")
                .build();

        //  RequestQueue requestQueue = Volley.newRequestQueue(this);
        client.newCall(request).enqueue( new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String myResponse = response.body().string();
                RecipeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject result = new JSONObject(myResponse);
                            Log.v(TAG, "getRecipeInstructions - " + result.toString());
                            try {
                                Picasso.get().load((String) result.get("image")).into(img);
                            } catch (Exception e) {
                                img.setImageResource(R.drawable.nopicture);
                            }

                            title.setText((String) result.getString("title"));
                            ready_in.setText(Integer.toString((Integer) result.get("readyInMinutes"))+"mins");
                            servings.setText(Integer.toString((Integer) result.get("servings")));

                            Integer jsonLikes = (Integer) result.get("aggregateLikes");
                            spoonacularAggregateLikes = jsonLikes.longValue();
                            aggregateLikes.setText(getAddedLikesString());
                            aggregateLikes.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

                            //RecipeID = (int) result.get("id");
                            RecipeAlarmTime = (int) result.get("readyInMinutes");
                            recipe_sourceUrl = (String) result.get("sourceUrl");

                            if(!setAnalysedInstructions(result, recipeId)){
                                if(!setInstructions(result)){
                                    setMsgForNoInstructions();
                                }
                            }

                            JSONArray ingredientsArr = (JSONArray) result.get("extendedIngredients");
                            List<Ingredient> ingredientsLst = new ArrayList<Ingredient>();
                            for (int i = 0; i < ingredientsArr.length(); i++) {
                                JSONObject ingredient = ingredientsArr.getJSONObject(i);
                                ingredientsLst.add(new Ingredient(ingredient.optString("originalString"), ingredient.optString("image")));
                            }
                            RecyclerViewAdapterRecipeIngredient myAdapter = new RecyclerViewAdapterRecipeIngredient(getApplicationContext(), ingredientsLst);
                            ingredients_rv.setAdapter(myAdapter);
                            ingredients_rv.setItemAnimator(new DefaultItemAnimator());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    private String getAddedLikesString(){
        return Long.toString(spoonacularAggregateLikes + recipeAppLikes);
    }

    private void sendRecipetoWatch(String recipeId) {

        if(analysedInstructions == null){
            Toast.makeText(this, "No instructions to send to watch", Toast.LENGTH_LONG).show();
        }
        else {
            Intent intentWear = new Intent(this, WearService.class);
            intentWear.setAction(WearService.ACTION_SEND.INSTRUCTIONS.name());
            intentWear.putExtra(WearService.EXTRA_INSTRUCTIONS, analysedInstructions);
            this.startService(intentWear);
            Constants.Recipe_ID = recipeId;
            Constants.Recipe_on_Watch = true;
        }
    }

    private void StartRecipeAlarm(View view){

        Log.i(TAG, "StartRecipeAlarm");
        Toast.makeText(getApplicationContext(), "You will be notified in "+ RecipeAlarmTime +"mins", Toast.LENGTH_SHORT).show();
        Intent i1 = new Intent(this, Alarm.class);
        i1.setAction("com.example.recipeapp.receiver.Message");
        i1.addCategory("android.intent.category.DEFAULT");
        PendingIntent pd = PendingIntent.getBroadcast(this,0,i1,0);
        myAlarmManager.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis() + RecipeAlarmTime*60*1000,pd);

    }


    private void StopRecipeAlarm(View view){
        Intent i1 = new Intent(this, Alarm.class);
        i1.setAction("com.example.recipeapp.receiver.Message");
        i1.addCategory("android.intent.category.DEFAULT");
        PendingIntent pd = PendingIntent.getBroadcast(this,0,i1,0);
        myAlarmManager.cancel(pd);
    }

    private boolean setAnalysedInstructions(JSONObject result, String recipeId) throws JSONException {
        JSONArray analysedInstructionsJSONArray = result.getJSONArray("analyzedInstructions");
        if(analysedInstructionsJSONArray.length() != 0) {
            analysedInstructions = new AnalysedInstructions(analysedInstructionsJSONArray.getJSONObject(0), recipeId);
            if (analysedInstructions.isInstructionsOk()) {
                final RVAdapterRecipeInstructions adapter = new RVAdapterRecipeInstructions(getApplicationContext(), analysedInstructions);
                Log.v(TAG, "getAnalysedInstructions - setOnItemClickListener");
                adapter.setOnItemClickListener(new RVAdapterRecipeInstructions.ClickListener() {
                    @Override
                    public void onItemClick(int position, View v) {
                        Log.v(TAG, "onItemClick at pos " + position);
                        adapter.itemClicked(position);
                        adapter.notifyDataSetChanged();
                    }
                });
                instructions_rv.setAdapter(adapter);
                instructions_rv.setItemAnimator(new DefaultItemAnimator());
                instructions.setVisibility(View.GONE);
                return true;
            }
        }
        return false;
    }

    private boolean setInstructions(JSONObject result) throws JSONException {
        String instructions_txt = result.getString("instructions");
        Log.i(TAG, "getRecipeInstructions - instructions " + instructions_txt);
        if(instructions_txt.equals("null") || instructions_txt.isEmpty()) {
            return false;
        }
        else {
            instructions.setText(instructions_txt);
            return true;
        }
    }

   // @RequiresApi(api = Build.VERSION_CODES.N)
    private void setMsgForNoInstructions(){
        String msg = "Unfortunately, the instructions you were looking for not found, view the original recipe <a href="+recipe_sourceUrl+">here</a>";
        instructions.setText(Html.fromHtml(msg, Html.FROM_HTML_MODE_COMPACT));
        instructions.setMovementMethod(LinkMovementMethod.getInstance());
    }


}