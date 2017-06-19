package jp.gr.java_conf.mu.csb.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import jp.gr.java_conf.mu.csb.util.DynamoDBUtil;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class GetRecord implements RequestHandler<Object, Object> {
	private LambdaLogger logger;

	public Object handleRequest(Object input, Context context) {
		logger = context.getLogger();
		logger.log("Input: " + input);

		// DynamoDB���p����
		DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(logger);

		// Twitter���p����
		// ���ϐ�����e��L�[��ݒ�
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true).setOAuthConsumerKey(System.getenv("twitter4j_oauth_consumerKey"))
				.setOAuthConsumerSecret(System.getenv("twitter4j_oauth_consumerSecret"))
				.setOAuthAccessToken(System.getenv("twitter4j_oauth_accessToken"))
				.setOAuthAccessTokenSecret(System.getenv("twitter4j_oauth_accessTokenSecret"));
		Configuration configuration = cb.build();

		// �O��܂łŎ�荞��ID���擾
		GetItemResult result = dynamoDBUtil.getItem("csb_status", "key", "get_record_since_id");
		long sinceId = Long.parseLong(result.getItem().get("value").getS());
		logger.log("ID: " + sinceId + " ����̕ԐM���擾");
		Paging paging = new Paging(sinceId);

		int count = 0;
		boolean success = false;
		TwitterFactory tf;
		ResponseList<Status> responseList = null;
		// ���g���C��
		int retryCount = 3;
		do {
			tf = new TwitterFactory(configuration);
			Twitter twitter = tf.getInstance();
			// �ԐM�̈ꗗ���擾
			try {
				count++;
				responseList = twitter.getMentionsTimeline(paging);
				success = true;
			} catch (TwitterException e1) {
				logger.log("�ԐM�擾���s : " + e1.getErrorMessage());
				// ���s�����ꍇ�͑ҋ@��ɍĎ��s
				if (count < retryCount) {
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e2) {
					}
				}
			}
		} while (!success && count < retryCount);

		// �擾�Ɏ��s�����ꍇ�͏I��
		if (!success) {
			return null;
		}

		logger.log(responseList.size() + " ���̕ԐM���擾");
		for (Status status : responseList) {
			logger.log("---------------------------------------------------------------------");
			logger.log("���[�U��:" + status.getUser().getName());
			logger.log("���[�U��(�\����):" + status.getUser().getScreenName());
			logger.log("�ԐM���e:" + status.getText());
			logger.log("�ԐMID:" + status.getId() + "");
			logger.log("�ԐM������:" + status.getCreatedAt() + "");
			logger.log("�ԐM�惆�[�U��(�\����):" + status.getInReplyToScreenName());
			logger.log("�ԐM��c�C�[�gID:" + status.getInReplyToStatusId() + "");

			// �ԐM��c�C�[�gID���Ƃ�Ȃ��ꍇ(����̃c�C�[�g�ɑ΂���ԐM�ł͂Ȃ��ꍇ)�̓X�L�b�v
			if (status.getInReplyToStatusId() == -1) {
				logger.log("�X�L�b�v(����c�C�[�g�ɑ΂���ԐM�ȊO)");
				continue;
			}

			// �c�C�[�g����A�ŏ��ɏo�Ă��鐔�������𒊏o
			Matcher matcher = Pattern.compile("^@[^ ]+? \\D*(\\d+\\.\\d+|\\d+).*$").matcher(status.getText());
			String recordStr;
			if (matcher.find()) {
				recordStr = matcher.group(1);
				logger.log("�L�^�𒊏o1: " + recordStr);
			} else {
				// �����������擾�ł��Ȃ������ꍇ�̓X�L�b�v
				logger.log("�X�L�b�v(�L�^�Ȃ�)");
				continue;
			}

			// ���l�^�ɕϊ��B�ϊ��ł��Ȃ������ꍇ�̓X�L�b�v
			double record;
			try {
				// ������3�ʂŎl�̌ܓ��������double�^�ɕϊ�
				record = Double.parseDouble(String.format("%.2f", Double.parseDouble(recordStr)));
				logger.log("�L�^�𒊏o2: " + record);
			} catch (Exception e) {
				continue;
			}

			// �擾���ꂽ�f�[�^��o�^

		}

		return null;
	}

}
