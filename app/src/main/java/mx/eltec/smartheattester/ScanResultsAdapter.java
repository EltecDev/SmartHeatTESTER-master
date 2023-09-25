package mx.eltec.smartheattester;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class ScanResultsAdapter extends RecyclerView.Adapter<ScanResultsAdapter.viewHolder> {

    Context context;
    List<ScanResult> results;
    String selectedSSID = "";

    ScanResultsAdapter(Context context, List<ScanResult> results){
        this.context = context;
        this.results = results;
        this.selectedSSID = results.get(0).SSID;
    }

    public class viewHolder extends RecyclerView.ViewHolder {

        public TextView title;
        public RadioButton checkBox;
        public View itemView;

        public viewHolder(View view) {
            super(view);
            checkBox = (RadioButton) view.findViewById(R.id.itemCheckBox);
            title = (TextView) view.findViewById(R.id.titleTextView);
            itemView = view;
        }
    }

    private class VIEW_TYPES {
        public static final int Normal = 1;
        public static final int Footer = 2;
        public static final int Header = 3;
    }

    @Override
    public int getItemViewType(int position) {

        if(position == this.results.size() + 1) {
            return VIEW_TYPES.Footer;
        }else if(position == 0){
            return VIEW_TYPES.Header;
        }else{
            return VIEW_TYPES.Normal;
        }
    }

    @Override
    public ScanResultsAdapter.viewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View itemView;
        switch(viewType){
            case VIEW_TYPES.Normal:
                itemView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.scan_result_item, parent, false);
                return new ScanResultsAdapter.viewHolder(itemView);

            case VIEW_TYPES.Footer:
                itemView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.scan_results_footer, parent, false);
                return new ScanResultsAdapter.viewHolder(itemView);

            case VIEW_TYPES.Header:
                itemView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.scan_results_header, parent, false);
                return new ScanResultsAdapter.viewHolder(itemView);

        }

        return null;
    }

    @Override
    public void onBindViewHolder(ScanResultsAdapter.viewHolder holder, final int position) {

        if(holder.getItemViewType() != VIEW_TYPES.Footer && holder.getItemViewType() != VIEW_TYPES.Header){

            holder.title.setText(this.results.get(position - 1).SSID);
            holder.checkBox.setChecked(this.results.get(position - 1).SSID.equals(this.selectedSSID));

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedSSID = results.get(position - 1).SSID;
                    notifyDataSetChanged();
                }
            });

        }
    }

    @Override
    public int getItemCount() {
        return results.size() + 2;
    }

}
