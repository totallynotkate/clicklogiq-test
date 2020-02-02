package edu.kotsiievska;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

/**
 * Created by totallynotkate on 2020-02-01.
 */
@RunWith(DataProviderRunner.class)
public class DistributionTest {
    private static final String DICE_ROLLING_URI = "https://www.random.org/dice/?num=%d";
    private static final String DICE_ROLLING_RESULT_CSS_SELECTOR = "img[src^=\"dice\"]";
    private static final String ROLL_VALUE_AS_STRING_ATTRIBUTE = "alt";
    private final int MAX_FUTURE_CALL_RETRIES = 5;

    @DataProvider
    public static Object[][] rollParameters() {
        double chiSquareFor5DegreesOfFreedomAnd5PercDeviation = 1.15;
        double chiSquareFor11DegreesOfFreedomAnd5PercDeviation = 4.57;
        return new Object[][]{
                {1000, 1, 6, chiSquareFor5DegreesOfFreedomAnd5PercDeviation},
//                {1000, 2, 11, chiSquareFor11DegreesOfFreedomAnd5PercDeviation} - always fails because the hypothesis
//                about uniform distribution for 2 dice is wrong
        };
    }

    @Test
    @UseDataProvider("rollParameters")
    //test uses chi square formula to check if distribution is uniform, see
    // https://internal.ncl.ac.uk/ask/numeracy-maths-statistics/business/hypothesis-tests/chi-square-tests.html
    // for explanation and
    // https://people.smp.uq.edu.au/YoniNazarathy/stat_models_B_course_spring_07/distributions/chisqtab.pdf
    // for chi model values
    public void checkIfRollDistributionIsUniform(int numberOfThrows, int numberOfDice, int numberOfRollResults,
                                                 double modelChiSquare) throws ExecutionException, InterruptedException {

        List<CompletableFuture<Integer>> futures = getCompletableFuturesPoolForThrows(numberOfThrows, numberOfDice);

        int[] results = new int[numberOfRollResults];

        for (CompletableFuture<Integer> f : futures) {
            int rollResult = f.get();
            results[rollResult - numberOfDice]++;
        }

        double actualChiSquare = calculateChiSquare(numberOfThrows, numberOfRollResults, results);

        assertThat("distribution is not uniform (p = 5%) because actual chi square is greater than model chi square",
                actualChiSquare, lessThan(modelChiSquare));
    }

    private List<CompletableFuture<Integer>> getCompletableFuturesPoolForThrows(int numberOfThrows, int numberOfDice) {
        List<CompletableFuture<Integer>> futures = new ArrayList<>(numberOfThrows);
        for (int i = 0; i < numberOfThrows; i++) {
            futures.add(getOneFutureWithRollResult(numberOfDice));
        }
        return futures;
    }

    private CompletableFuture<Integer> getOneFutureWithRollResult(int numberOfDice) {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> queryPageAndParseRollResult(numberOfDice));
        for (int i = 0; i < MAX_FUTURE_CALL_RETRIES; i++) {
            future = future.thenApply(CompletableFuture::completedFuture)
                    .exceptionally(t -> CompletableFuture.supplyAsync(() -> queryPageAndParseRollResult(numberOfDice)))
                    .thenCompose(Function.identity());
        }
        return future;
    }

    private Integer queryPageAndParseRollResult(int numberOfDice) {
        try {
            //todo: page object
            Document htmlPage = Jsoup.connect(String.format(DICE_ROLLING_URI, numberOfDice)).get();
            Elements diceElements = htmlPage.select(DICE_ROLLING_RESULT_CSS_SELECTOR);
            int diceSum = 0;
            for (Element el : diceElements) {
                diceSum += Integer.valueOf(el.attr(ROLL_VALUE_AS_STRING_ATTRIBUTE));
            }
            return diceSum;
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private double calculateChiSquare(int numberOfThrows, int numberOfPossibleResults, int[] results) {
        double squaredResultSum = 0;
        double probability = (double) 1 / numberOfPossibleResults;
        for (int i : results) {
            squaredResultSum = squaredResultSum + Math.pow(i, 2) / probability;
        }
        return (double) 1 / numberOfThrows * squaredResultSum - numberOfThrows;
    }
}
