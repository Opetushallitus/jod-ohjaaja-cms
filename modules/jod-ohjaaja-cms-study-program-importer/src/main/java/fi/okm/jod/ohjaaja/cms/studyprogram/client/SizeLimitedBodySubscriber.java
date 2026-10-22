/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.studyprogram.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Receives the body of a response as a byte array, but fails as soon as the body is larger than the
 * given size, without receiving the rest of it. A body of an unknown length can then be received
 * without it filling the memory.
 */
final class SizeLimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

  private final int maxSize;
  private final String tooLargeMessage;
  private final ByteArrayOutputStream body = new ByteArrayOutputStream();
  private final CompletableFuture<byte[]> result = new CompletableFuture<>();
  private Flow.Subscription subscription;

  /**
   * @param tooLargeMessage the message of the exception that the body fails with if it is larger
   */
  SizeLimitedBodySubscriber(int maxSize, String tooLargeMessage) {
    this.maxSize = maxSize;
    this.tooLargeMessage = tooLargeMessage;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(List<ByteBuffer> buffers) {
    for (var buffer : buffers) {
      // Buffers can still arrive after the subscription has been cancelled
      if (result.isDone()) {
        return;
      }
      if (buffer.remaining() > maxSize - body.size()) {
        subscription.cancel();
        result.completeExceptionally(new IOException(tooLargeMessage));
        return;
      }
      var bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      body.writeBytes(bytes);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    result.completeExceptionally(throwable);
  }

  @Override
  public void onComplete() {
    result.complete(body.toByteArray());
  }

  @Override
  public CompletionStage<byte[]> getBody() {
    return result;
  }
}
