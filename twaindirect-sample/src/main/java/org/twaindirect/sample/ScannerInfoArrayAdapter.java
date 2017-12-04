package org.twaindirect.sample;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.twaindirect.discovery.ScannerInfo;

import java.util.List;

/**
 * Adapts ScannerInfo into R.layouts.scanner_list_row, used by the scanner picker.
 */

public class ScannerInfoArrayAdapter extends ArrayAdapter<ScannerInfo> {

    public ScannerInfoArrayAdapter(@NonNull Context context, List<ScannerInfo> scanners) {
        super(context, 0, scanners);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ScannerInfo scannerInfo = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.scanner_list_row, parent, false);
        }

        TextView titleView = convertView.findViewById(R.id.title);
        TextView detailsView = convertView.findViewById(R.id.details);

        titleView.setText(scannerInfo.getFriendlyName());

        String details = scannerInfo.getUrl().toString() + "\n" + scannerInfo.getFqdn();
        String note = scannerInfo.getNote();
        if (note != null) {
            details = details + "\n" + note;
        }

        detailsView.setText(details);

        return convertView;
    }
}
