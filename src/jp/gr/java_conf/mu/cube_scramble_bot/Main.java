package jp.gr.java_conf.mu.cube_scramble_bot;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class Main implements RequestHandler<Object, Object> {
	private LambdaLogger logger;

	public Object handleRequest(Object input, Context context) {
		logger = context.getLogger();
		logger.log("Input: " + input);

		// ���ϐ�����e��L�[��ݒ�
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true).setOAuthConsumerKey(System.getenv("twitter4j_oauth_consumerKey"))
				.setOAuthConsumerSecret(System.getenv("twitter4j_oauth_consumerSecret"))
				.setOAuthAccessToken(System.getenv("twitter4j_oauth_accessToken"))
				.setOAuthAccessTokenSecret(System.getenv("twitter4j_oauth_accessTokenSecret"));
		TwitterFactory tf = new TwitterFactory(cb.build());
		Twitter twitter = tf.getInstance();

		// �X�N�����u������
		String scramble = generateScramble(20);
		logger.log("�X�N�����u��: " + scramble);

		int count = 0;
		boolean success = false;
		// ���g���C��
		int retryCount = 3;
		do {
			// �c�C�[�g
			try {
				count++;
				Status status = twitter.updateStatus(scramble);
				logger.log("Successfully updated the status to [" + status.getText() + "].");
				success = true;
			} catch (TwitterException e) {
				logger.log("�c�C�[�g���s");
				logger.log(e.getErrorMessage());

				// ���s�����ꍇ�͑ҋ@��ɍĎ��s
				if (count < retryCount) {
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e2) {
					}
				}
			}
			// �ő��3�񃊃g���C����
		} while (!success && count < retryCount);

		return null;
	}

	// �X�N�����u���𐶐�
	private String generateScramble(int l) {
		String faces[] = { "U", "D", "R", "L", "F", "B" };
		String options[] = { "", "'", "2" };
		String scramble = "";
		int beforeFace = -1;
		int before2Face = -1;
		int currentFace;
		for (int i = 0; i < l; i++) {
			// �񂷖ʂ����߂�B1�O�Ɠ����ʂ�I�΂Ȃ��悤�ɂ���
			do {
				currentFace = randomN(6);
			} while (!faceCheck(currentFace, beforeFace, before2Face));
			scramble += (faces[currentFace] + options[randomN(3)]);
			if (i < l - 1) {
				scramble += " ";
			}
			// 2�O�A1�O�̎菇���L�^
			before2Face = beforeFace;
			beforeFace = currentFace;
		}
		return scramble;
	}

	// n���(0�`n-1)�̗����𐶐�
	private int randomN(int n) {
		return (int) (Math.random() * n);
	}

	// �񂷖ʂ̃`�F�b�N
	private boolean faceCheck(int current, int before, int before2) {
		// 1�O�Ɠ����ʂ͉񂳂Ȃ�
		if (current == before) {
			return false;
		}
		// ������ -> �Ζ� -> ������ �̏��̉�]��NG(��: U2, D, U')
		if (current == before2
				&& ((current % 2 == 0 && current - before == -1) || (current % 2 == 1 && current - before == 1))) {
			return false;
		}
		return true;
	}
}
