///*
//   Copyright 2010 Christopher Kruse and Sean Mooney
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License. 
// */
//package com.cornerofseven.castroid.adapter;
//
//import android.content.Context;
//import android.database.Cursor;
//import android.util.Log;
//import android.widget.SimpleCursorAdapter;
//import android.widget.TextView;
//
///**
// * @author sean
// *
// */
//public class NewItemHighlightingAdapter extends SimpleCursorAdapter{
//
//	/**
//	 * @param context
//	 * @param layout
//	 * @param c
//	 * @param from
//	 * @param to
//	 */
//	public NewItemHighlightingAdapter(Context context, int layout, int newLayout, Cursor c,
//			String[] from, int[] to) {
//		super(context, layout, c, from, to);
//	}
//	
//	@Override
//	public void setViewText(TextView t, String s){
//		Log.v("Castroid", "Setting textview with string " + s);
//		super.setViewText(t,s);
//	}
//}
