package cm.aptoide.pt.home;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cm.aptoide.pt.R;
import java.text.DecimalFormat;

public class AppSecondaryInfoViewHolder {
  private final DecimalFormat oneDecimalFormatter;
  private final LinearLayout appcLayout;
  private final TextView appcText;
  private final LinearLayout ratingLayout;
  private final TextView rating;

  public AppSecondaryInfoViewHolder(View itemView, DecimalFormat oneDecimalFormatter) {
    this.oneDecimalFormatter = oneDecimalFormatter;
    appcLayout = (LinearLayout) itemView.findViewById(R.id.appc_info_layout);
    appcText = (TextView) itemView.findViewById(R.id.appc_text);
    ratingLayout = (LinearLayout) itemView.findViewById(R.id.rating_info_layout);
    rating = (TextView) itemView.findViewById(R.id.rating_label);
  }

  public void setInfo(boolean hasAppcBilling, float appRating, boolean showRating,
      boolean showBoth) {
    appcText.setText(R.string.appc_short_spend_appc);
    if (appRating == 0) {
      this.rating.setText(R.string.appcardview_title_no_stars);
    } else {
      this.rating.setText(oneDecimalFormatter.format(appRating));
    }

    if (showBoth) {
      if (hasAppcBilling) {
        appcLayout.setVisibility(View.VISIBLE);
      } else {
        appcLayout.setVisibility(View.INVISIBLE);
      }
      ratingLayout.setVisibility(View.VISIBLE);
    } else if (showRating) {
      appcLayout.setVisibility(View.INVISIBLE);
      ratingLayout.setVisibility(View.VISIBLE);
    } else if (hasAppcBilling) {
      appcLayout.setVisibility(View.VISIBLE);
      ratingLayout.setVisibility(View.INVISIBLE);
    } else {
      appcLayout.setVisibility(View.INVISIBLE);
    }
  }
}
