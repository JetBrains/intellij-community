// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyUserTypeRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SetUserTypeRenderersCommand extends AbstractCommand {

  private final @NotNull List<@NotNull PyUserTypeRenderer> myRenderers;
  private static final String COMMAND_HEADER = "RENDERERS";
  private static final int RENDERER_FIELD_COUNT = 9;
  private static final int CHILD_FIELD_COUNT = 1;

  public SetUserTypeRenderersCommand(final @NotNull RemoteDebugger debugger, final @NotNull List<@NotNull PyUserTypeRenderer> renderer) {
    super(debugger, AbstractCommand.SET_USER_TYPE_RENDERERS);
    myRenderers = renderer;
  }

  public static String createMessage(@NotNull List<@NotNull PyUserTypeRenderer> renderers) {
    Payload payload = new Payload();
    payload.add(COMMAND_HEADER);
    for (PyUserTypeRenderer renderer : renderers) {
      payload
        .add(RENDERER_FIELD_COUNT + CHILD_FIELD_COUNT * renderer.getChildren().size())
        .add(buildCondition(renderer.getToType()))
        .add(buildCondition(renderer.getTypeCanonicalImportPath()))
        .add(buildCondition(renderer.getTypeQualifiedName()))
        .add(buildCondition(renderer.getTypeSourceFile()))
        .add(renderer.getModuleRootHasOneTypeWithSameName())
        .add(renderer.isDefaultValueRenderer())
        .add(buildCondition(renderer.getExpression()))
        .add(renderer.isDefaultChildrenRenderer())
        .add(renderer.isAppendDefaultChildren());
      for (PyUserTypeRenderer.ChildInfo child : renderer.getChildren()) {
        payload.add(buildCondition(child.getExpression()));
      }
    }
    return payload.getText();
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(createMessage(myRenderers));
  }
}
