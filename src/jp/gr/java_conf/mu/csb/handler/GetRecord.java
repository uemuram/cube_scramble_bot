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

		// -----------------------------�ԐM�擾���� ��������-----------------------------
		// ������
		TwitterFactory tf = new TwitterFactory(configuration);
		Twitter twitter = tf.getInstance();
		ResponseList<Status> responseList;
		ResponseList<Status> tmpResponseList;

		// ��x�Ɏ擾����ԐM��
		int count = 20;
		long maxId = 0L;
		Paging paging;

		// �܂�1�y�[�W�ڂ��擾
		paging = new Paging(1, count);
		paging.setSinceId(sinceId);
		try {
			responseList = twitter.getMentionsTimeline(paging);
			logger.log("�ԐM�擾 " + responseList.size() + " ��");
		} catch (TwitterException e1) {
			logger.log("�ԐM�擾���s : " + e1.getErrorMessage());
			throw new RuntimeException(e1);
		}
		boolean endFlag = false;
		if (responseList.size() < count) {
			// �擾���ʂ��擾������菭�Ȃ������ꍇ�́A����ȏ�Ȃ��̂ŏI��
			endFlag = true;
		} else {
			// �擾�ł����ꍇ�͍Ō��ID���擾���ď������s
			maxId = responseList.get(responseList.size() - 1).getId() - 1;
		}

		// 1�y�[�W�ڂ��擾�ł����ꍇ��2�y�[�W�ڈȍ~���擾
		while (!endFlag) {
			paging = new Paging();
			paging.setCount(count);
			paging.setMaxId(maxId);
			paging.setSinceId(sinceId);
			try {
				tmpResponseList = twitter.getMentionsTimeline(paging);
				logger.log("�ԐM�擾 " + tmpResponseList.size() + " �� (maxId= " + maxId + " )");
			} catch (TwitterException e1) {
				logger.log("�ԐM�擾���s : " + e1.getErrorMessage());
				throw new RuntimeException(e1);
			}
			// �擾���ʂ�ǉ�
			responseList.addAll(tmpResponseList);
			if (tmpResponseList.size() < count) {
				// �擾���ʂ��擾������菭�Ȃ������ꍇ�́A����ȏ�Ȃ��̂ŏI��
				endFlag = true;
			} else {
				// �擾�ł����ꍇ�͍Ō��ID���擾���ď������s
				maxId = tmpResponseList.get(tmpResponseList.size() - 1).getId() - 1;
			}
		}
		// -----------------------------�ԐM�擾���� �����܂�-----------------------------

		logger.log(responseList.size() + " ���̕ԐM���擾");
		int lastIndex = responseList.size() - 1;
		// �Â����Ƀ��[�v
		for (int i = lastIndex; i >= 0; i--) {
			Status status = responseList.get(i);
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
