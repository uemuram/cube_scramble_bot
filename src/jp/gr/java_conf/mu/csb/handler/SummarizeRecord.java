package jp.gr.java_conf.mu.csb.handler;

import java.awt.BasicStroke;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import jp.gr.java_conf.mu.csb.util.DynamoDBUtil;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class SummarizeRecord implements RequestHandler<Object, Object> {
	private LambdaLogger logger;

	private static final String TABLE_NAME_RECORD = System.getenv("table_name_record");
	private static final String TABLE_NAME_USER = System.getenv("table_name_user");

	private static final int SUMMARIZE_TRIGGER_TWEET_COUNT = 5;
	private static final int RECORD_COUNT_FOR_GRAPH = 20;

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
		TwitterFactory tf = new TwitterFactory(configuration);
		Twitter twitter = tf.getInstance();

		// DB���烆�[�U�ꗗ���擾(�������̃c�C�[�g������萔��葽�����[�U�̂ݒ��o
		List<Map<String, AttributeValue>> users = dynamoDBUtil.scan(TABLE_NAME_USER, "not_summarized_tweet_count",
				ComparisonOperator.GE, SUMMARIZE_TRIGGER_TWEET_COUNT);

		// �Ώۃ��[�U�ɑ΂��ă��[�v
		int count = 0;
		for (Map<String, AttributeValue> item : users) {
			count++;
			logger.log(count + "----------------------------------------------------------");

			// DB�擾���ʂ𐮗�
			String userName = item.get("user_name").getS();
			String oldestSummarizedTweetId = item.get("oldest_summarized_tweet_id").getS();

			logger.log("���[�U��: " + userName);
			logger.log("�ŌÂ̕��͍ς݃c�C�[�gID: " + oldestSummarizedTweetId);

			// �Ώۃ��[�U�̋L�^������
			List<Map<String, AttributeValue>> records = dynamoDBUtil.query(TABLE_NAME_RECORD, "user_name", userName,
					"reply_id", ComparisonOperator.GE, oldestSummarizedTweetId, "user_name-reply_id-index");

			// �O���t�摜�𐶐�
			File graphFile = createGraph(records, userName);

			// �c�C�[�g�Ɖ摜�Y�t
			Status status;
			String tweetText = "@" + userName + " " + "�ŋ߂̋L�^�ł��B" + calcAvarage(records);

			// ����𒴂��Ȃ��悤��140�����Ő؂���
			logger.log("�c�C�[�g�e�L�X�g: " + tweetText);
			if (tweetText.length() > 140) {
				tweetText = tweetText.substring(0, 140);
				logger.log("�c�C�[�g�e�L�X�g(�؂����): " + tweetText);
			}

			try {
				status = twitter.updateStatus(new StatusUpdate(tweetText).media(graphFile));
				logger.log("---------------------------------------------------------------------");
				logger.log("�c�C�[�g���e:" + status.getText());
				logger.log("�c�C�[�gID:" + status.getId() + "");
				logger.log("�c�C�[�g��������:" + status.getCreatedAt() + "");
			} catch (TwitterException e1) {
				logger.log("�c�C�[�g���s : " + e1.getErrorMessage());
				throw new RuntimeException(e1);
			}

			// ���[�U�����X�V
			// ����̋N�_�ƂȂ�c�C�[�gID
			int size = item.size();
			int idx = size >= RECORD_COUNT_FOR_GRAPH ? size - RECORD_COUNT_FOR_GRAPH : 0;
			String oldestSummarizedTweetIdUpd = records.get(idx).get("reply_id").getS();

			// �X�V���{
			Map<String, AttributeValue> putItem = new HashMap<String, AttributeValue>();
			putItem.put("user_name", new AttributeValue().withS(userName));
			putItem.put("not_summarized_tweet_count", new AttributeValue().withN("0"));
			putItem.put("oldest_summarized_tweet_id", new AttributeValue().withS(oldestSummarizedTweetIdUpd));
			dynamoDBUtil.putItem(TABLE_NAME_USER, putItem);
		}

		return null;
	}

	// ���ς��v�Z���A������Ƃ��ĕԂ�
	private String calcAvarage(List<Map<String, AttributeValue>> records) {
		int size = records.size();
		// �f�[�^����5�ɖ����Ȃ��ꍇ�͌v�Z���Ȃ�
		if (size < 5) {
			return "";
		}
		// �ő�ƍŏ����`�F�b�N
		int start = size - 5;
		int maxIdx = start, minIdx = start;
		double max = 0, min = Double.POSITIVE_INFINITY;
		for (int i = start; i < size; i++) {
			Map<String, AttributeValue> item = records.get(i);
			double record = Double.parseDouble(item.get("record").getN());
			if (record > max) {
				maxIdx = i;
				max = record;
			}
			if (record < min) {
				minIdx = i;
				min = record;
			}
		}

		// �S�ē����l�������Ƃ��ɍő�l�ƍŏ��l��ʈ����ɂ��邽�߂̏���
		if (maxIdx == minIdx && maxIdx == start) {
			minIdx = start + 1;
		}
		// ���ς��v�Z
		double recordSum = 0;
		String resultStr = "\n";
		for (int i = start; i < size; i++) {
			Map<String, AttributeValue> item = records.get(i);
			double record = Double.parseDouble(item.get("record").getN());
			String recordStr;
			if (i == maxIdx || i == minIdx) {
				// �ő�or�ŏ��̂��߃X�L�b�v
				recordStr = "(" + String.format("%.2f", record) + ")";
			} else {
				// �X�L�b�v�����v�Z�ɗ��p
				recordSum += record;
				recordStr = String.format("%.2f", record);
			}
			resultStr += recordStr;
			if (i < size - 1) {
				resultStr += " ";
			}
		}
		double avarage = recordSum / 3;
		resultStr += "\n����:" + String.format("%.2f", avarage);

		return resultStr;
	}

	// �O���t�摜���쐬
	private File createGraph(List<Map<String, AttributeValue>> records, String userName) {
		// ���o���f�[�^�͈͂̌���
		int size = records.size();
		int start = size >= RECORD_COUNT_FOR_GRAPH ? size - RECORD_COUNT_FOR_GRAPH : 0;
		int end = size - 1;

		// �O���t�p�f�[�^����
		String series1 = "record";
		double recordMax = 0;
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		// �L�^�Ń��[�v
		for (int i = start; i <= end; i++) {
			Map<String, AttributeValue> item = records.get(i);

			// �f�[�^�����o��
			double record = Double.parseDouble(item.get("record").getN());
			String replyDate = item.get("reply_date").getS();
			logger.log(replyDate + " : " + record);
			dataset.addValue(record, series1, replyDate);
			// �ő�l���Ƃ��Ă���
			if (record > recordMax) {
				recordMax = record;
			}
		}

		// JFreeChart�I�u�W�F�N�g�̐���
		JFreeChart chart = ChartFactory.createLineChart("Record(@" + userName + ")", "date", "sec", dataset,
				PlotOrientation.VERTICAL, false, true, false);

		CategoryPlot plot = chart.getCategoryPlot();
		LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
		// ���̑�����ݒ�
		renderer.setSeriesStroke(0, new BasicStroke(2));
		// ���̐}�`��\��
		renderer.setSeriesShapesVisible(0, true);
		// ���̕t�߂ɒl��\��
		renderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
		renderer.setBaseItemLabelsVisible(true);
		// ���x���̌�����ς���
		CategoryAxis axis = plot.getDomainAxis();
		axis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
		// �������g���͂ݏo���̂�h�����߁A�c���̍ő�l���A�L�^�̍ő�l��菭���傫������
		NumberAxis numberAxis = (NumberAxis) plot.getRangeAxis();
		numberAxis.setUpperBound(recordMax * 1.1);

		// �O���t�o��
		String fileName = "record_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
				+ ".png";
		String filePath = "/tmp/" + fileName;
		File outputFile = new File(filePath);
		try {
			ChartUtilities.saveChartAsPNG(outputFile, chart, 1000, 500);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		logger.log("�t�@�C���o�� : " + filePath);

		return outputFile;
	}

}
