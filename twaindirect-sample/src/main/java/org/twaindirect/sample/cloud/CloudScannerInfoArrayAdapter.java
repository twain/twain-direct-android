package org.twaindirect.sample.cloud;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.twaindirect.cloud.CloudScannerInfo;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.sample.R;

import java.util.List;

/**
 * Adapts CloudScannerInfo into R.layouts.cloud_scanner_list_row, used by the scanner picker.
 */

public class CloudScannerInfoArrayAdapter extends ArrayAdapter<CloudScannerInfo> {

    public CloudScannerInfoArrayAdapter(@NonNull Context context, List<CloudScannerInfo> scanners) {
        super(context, 0, scanners);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        CloudScannerInfo scannerInfo = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.scanner_list_row, parent, false);
        }

        TextView titleView = convertView.findViewById(R.id.title);
        TextView detailsView = convertView.findViewById(R.id.details);

        titleView.setText(scannerInfo.getName());
        String note = scannerInfo.getDescription();
        if (note != null) {
            detailsView.setText(note);
        }

        return convertView;
    }
}
