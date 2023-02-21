package uk.gov.homeoffice.digital.sas.kafka.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.homeoffice.digital.sas.kafka.message.KafkaAction;
import uk.gov.homeoffice.digital.sas.kafka.producer.KafkaProducerService;
import uk.gov.homeoffice.digital.sas.model.Profile;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KafkaEntityListenerTest {

  private static final String PROFILE_ID = "profileId";
  private static final String PROFILE_NAME = "profileX";
  private Profile profile;

  @Mock
  private KafkaProducerService<Profile> kafkaProducerService;

  ProfileKafkaEntityListener kafkaEntityListener;

  @BeforeEach
  void setup() {
    profile = new Profile(PROFILE_ID, PROFILE_NAME);
    kafkaEntityListener = new ProfileKafkaEntityListener(kafkaProducerService);
  }

  @Test
  void resolveMessageKey_profileEntity_profileIdReturnedAsMessageKey() {
    assertThat(kafkaEntityListener.resolveMessageKey(profile)).isEqualTo(PROFILE_ID);
  }

  @Test
  void sendKafkaMessageOnCreate_profileEntity_sendMessageMethodInvokedAsExpected() {
    kafkaEntityListener.sendKafkaMessageOnCreate(profile);

    Mockito.verify(kafkaProducerService)
        .sendMessage(PROFILE_ID,
            profile,
            KafkaAction.CREATE);
  }

  @Test
  void sendKafkaMessageOnUpdate_profileEntity_sendMessageMethodInvokedAsExpected() {
    kafkaEntityListener.sendKafkaMessageOnUpdate(profile);

    Mockito.verify(kafkaProducerService)
        .sendMessage(PROFILE_ID,
            profile,
            KafkaAction.UPDATE);
  }

  @Test
  void sendKafkaMessageOnDelete_profileEntity_sendMessageMethodInvokedAsExpected() {
    kafkaEntityListener.sendKafkaMessageOnDelete(profile);

    Mockito.verify(kafkaProducerService)
        .sendMessage(PROFILE_ID,
            profile,
            KafkaAction.DELETE);
  }
}