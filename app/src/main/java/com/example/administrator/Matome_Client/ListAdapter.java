package com.example.administrator.Matome_Client;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

// リスト表示制御用クラス
class ListAdapter extends ArrayAdapter<Data> {
    private LayoutInflater inflater;
    // values/colors.xmlより設定値を取得するために利用。
    private Context mContext;

    public ListAdapter(Context context, List<Data> objects) {
        super(context, 0, objects);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
    }

    @Override
    public View getView(final int position, View view, ViewGroup parent) {
        // layout/raw.xmlを紐付ける
        if (view == null) {
            view = inflater.inflate(R.layout.raw, parent, false);
        }
        final Data data = this.getItem(position);
        TextView tvData1 = (TextView) view.findViewById(R.id.raw1);
        TextView tvData2 = (TextView) view.findViewById(R.id.raw2);
        TextView tvData3 = (TextView) view.findViewById(R.id.raw3);
        if (data != null) {
            //No.
            tvData1.setText(data.getNumber());
            //排出粉
            tvData2.setText(data.getZainmk());
            //缶タグ
            tvData3.setText(data.getCanTag());
        }

        //偶数行の場合の背景色を設定
        if (position % 2 == 0) {
            tvData1.setBackgroundColor(ContextCompat.getColor(mContext, R.color.data1));
            tvData2.setBackgroundColor(ContextCompat.getColor(mContext, R.color.data1));
            tvData3.setBackgroundColor(ContextCompat.getColor(mContext, R.color.data1));
        }
        //奇数行の場合の背景色を設定
        else {
            tvData1.setBackgroundColor(ContextCompat.getColor(mContext, R.color.data2));
            tvData2.setBackgroundColor(ContextCompat.getColor(mContext, R.color.data2));
            tvData3.setBackgroundColor(ContextCompat.getColor(mContext, R.color.data2));
        }

        return view;
    }
}
