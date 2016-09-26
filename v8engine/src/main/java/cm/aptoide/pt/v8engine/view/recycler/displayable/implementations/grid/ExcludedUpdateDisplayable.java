/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 13/07/2016.
 */

package cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid;

import cm.aptoide.pt.database.realm.Update;
import cm.aptoide.pt.model.v7.Type;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.view.recycler.displayable.DisplayablePojo;

/**
 * Created by sithengineer on 15/06/16.
 */
public class ExcludedUpdateDisplayable extends DisplayablePojo<Update> {

  private boolean selected;

  public ExcludedUpdateDisplayable() {
  }

  public ExcludedUpdateDisplayable(Update pojo) {
    super(pojo);
  }

  public ExcludedUpdateDisplayable(Update pojo, boolean fixedPerLineCount) {
    super(pojo, fixedPerLineCount);
  }

  @Override public Type getType() {
    return Type.EXCLUDED_UPDATE;
  }

  @Override public int getViewLayout() {
    return R.layout.row_excluded_update;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }
}
