package jp.gr.java_conf.mu.cube_scramble_bot;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

// �F�X�Ăяo���Ă݂�e�X�g
public class Test implements RequestHandler<Object, Object> {
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

		int count = 0;
		boolean success = false;
		// ���g���C��
		int retryCount = 3;
		do {
			Twitter twitter = tf.getInstance();

			// �e�X�g
			Paging arg0 = new Paging(1, 40);
			ResponseList<Status> responseList = null;
			try {
				count++;
				// responseList = twitter.getRetweetsOfMe(arg0);
				logger.log("a");
				// responseList = twitter.getRetweetsOfMe();
				// responseList = twitter.getHomeTimeline();
				responseList = twitter.getUserTimeline("masaru_uemura", new Paging(3, 20));

				
				
				success = true;
				logger.log("c");
			} catch (TwitterException e1) {
				logger.log("�c�C�[�g���s : " + e1.getErrorMessage());

				// ���s�����ꍇ�͑ҋ@��ɍĎ��s
				if (count < retryCount) {
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e2) {
					}
				}
			}
			logger.log("size:" + responseList.size());
			for (Status b : responseList) {
				logger.log("10:" + b.getUser().getName());
				logger.log("20:" + b.getUser().getScreenName());
				logger.log("30:" + b.getUser().getId() + "");
				logger.log("40:" + b.getText());
				logger.log("50:" + b.getId() + "");
				logger.log("60:" + b.getCreatedAt() + "");
				logger.log("70:" + b.getRetweetCount() + "");
			}

		} while (!success && count < retryCount);

		return null;
	}

}
