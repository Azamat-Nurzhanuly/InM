package com.android.barracuda.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.barracuda.R;


public class CallFragment extends Fragment {
  public static final int REQUEST_GO_CALL_ACTIVITY = 0;

  public CallFragment() {
    // Required empty public constructor
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);


  }


  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    View view = inflater.inflate(R.layout.fragment_call, container, false);

    Button button = (Button) view.findViewById(R.id.button);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // make a call!

        Intent intent = new Intent(getContext(), CallActivity.class);
        startActivityForResult(intent, REQUEST_GO_CALL_ACTIVITY);
      }
    });

    return view;
  }


  @Override
  public void onDestroyView() {
    super.onDestroyView();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

}
