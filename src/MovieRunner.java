import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


/**
 * The MovieRunner can be ran from the commandline to predict user ratings.
 * Example command to run:
 *      java -cp .:bin/ MovieRunner -trainingFile data/ra.train -matrixFile data/ra.matrix -testFile data/ra.test
 *
 * @author Toon Van Craenendonck
 * @author Pieter Robberechts
 */

public class MovieRunner {

    static MovieHandler ratings;
    static PearsonsCorrelation similarities;
    static boolean onlinePearson = false;
    static String testFile;
    static int[] external_to_internal_ids;
    static float[] ratingAveragesOfUsersInternalIDs;


    /**
     * Predict the rating of user with external id externUserID for movie with id movieID.
     *
     * @param externUserID external id of user whose rating should be predict
     * @param movieID movie for which the rating should be predicted
     * @return the predicted rating
     */

    //IMPLEMENT THIS!

    public static double predictRating(int externUserID, int movieID){
        int internalUserID = external_to_internal_ids[externUserID];

        double ratingsOfNN = 0;
        double sumOfCorrelations = 0;

        List<Neighbor> nnUsersCorrelations = similarities.getCorrelationsOfUsers()[internalUserID];
        Map<Integer, List<MovieRating>> usersToRatings = ratings.getUsersToRatings();
        for(int i=0; i<nnUsersCorrelations.size(); i++){ // i is internal ID

            //get the ith NN
            Neighbor nn = nnUsersCorrelations.get(i);
            int nnInternalID = nn.id;

            //get external ID of the neighbour
            Integer nnExternalID = ratings.getUserIDs().get(nnInternalID);

            //Movies rated by the neighbour
            List<MovieRating> nnRatings = usersToRatings.get(nnExternalID);

            //Check if neighbour has rated this movie
            for(MovieRating mr : nnRatings){
                if(mr.getMovieID() == movieID){
                    double d1 = mr.getRating() - ratingAveragesOfUsersInternalIDs[nnInternalID];
                    ratingsOfNN += nn.similarity * d1;
                    sumOfCorrelations += Math.abs(nn.similarity);
                    break;
                }
            }
        }

        double prediction;
        float userAverageRating = ratingAveragesOfUsersInternalIDs[internalUserID];

        if(sumOfCorrelations == 0) {
            return userAverageRating;
        }else{
            prediction = userAverageRating + (ratingsOfNN/sumOfCorrelations);
        }

        if(prediction > 5){
            return 5;
        }

        if(prediction < 0.5) {
            return 0.5;
        }

        return prediction;

    }


    /**
     * For each user/movie combination in the test set, predict the users'
     * rating for the movie and compare to the true rating.
     * Prints the current mean absolute error (MAE) after every 50 users.
     *
     * @param testFile path to file containing test set
     */
    public static void evaluate(String testFile) {

        double summedErrorRecommenderSq = 0;
        double summedErrorAvgSq = 0;

        int avg_used = 0;
        int est_used = 0;
        int ctr = 0;

        BufferedReader br;
        int startTime = (int) (System.currentTimeMillis()/1000);
        int elapsedTime = 0;
        try {
            br = new BufferedReader(new FileReader(testFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("::|\t");

                int userID = Integer.parseInt(tokens[0]);
                int movieID = Integer.parseInt(tokens[1]);
                double rating = Double.parseDouble(tokens[2]);

                double avgRating = ratings.getMovieAverageRating(movieID);
                double estimate = predictRating(userID, movieID);

                summedErrorRecommenderSq += Math.pow(rating - estimate,2);
                summedErrorAvgSq += Math.pow(rating - avgRating, 2);
                ctr++;

                if (avgRating == estimate) {
                    avg_used++;
                } else {
                    est_used++;
                }
                if ((ctr % 50) == 0) {
                    elapsedTime = (int)(System.currentTimeMillis()/1000) - startTime;
                    int remainingTime = (int) (elapsedTime * 698780f / ctr) - elapsedTime;
                    System.out.println("RMSE (default): " + Math.sqrt(summedErrorAvgSq/ctr)
                            + " RMSE (recommender): " + Math.sqrt(summedErrorRecommenderSq/ctr)
                            + " Time remaining: " + (int) ((remainingTime / (60*60)) % 24) + "h" + (int) ((remainingTime / 60) % 60)
                    );
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void computeAverages(){
        //Calculate and save all the deviations of users
        List<Integer> userIDs = ratings.getUserIDs();
        ratingAveragesOfUsersInternalIDs = new float[ratings.getNumUsers()];
        for(int j=0; j<userIDs.size(); j++){ //j is the internal user id
            int externalUserID = userIDs.get(j);
            List<MovieRating> userRatings = ratings.getUsersToRatings().get(externalUserID);
            float sumOfUserRatings = 0;
            for(MovieRating mr : userRatings){
                sumOfUserRatings += mr.getRating();
            }
            ratingAveragesOfUsersInternalIDs[j] = sumOfUserRatings / userRatings.size();
        }
    }

    public static void main(String[] args) {

        String trainingFile = "";
        String testFile = "";
        String matrixFile = null;
        int kNN = 1000;

        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            String arg = args[i];
            if(arg.equals("-trainingFile")) {
                trainingFile = args[i+1];
            } else if(arg.equals("-testFile")) {
                testFile = args[i+1];
            } else if(arg.equals("-matrixFile")) {
                matrixFile = args[i+1];
            } else if(arg.equals("-onlinePearson")) {
                onlinePearson = true;
            } else if(arg.equals("-kNN")){
                kNN = Integer.parseInt(args[i+1]);
            }
            // ADD ADDITIONAL PARAMETERS HERE //
            i += 2;
        }


        ratings = new MovieHandler(trainingFile);
        similarities = new PearsonsCorrelation(ratings, matrixFile, kNN);

        //Keep a track of Externals To Internals movie IDs
        List<Integer> userIds = ratings.getUserIDs();
        int maxExternalUserID = userIds.get(ratings.getUserIDs().size()-1);
        external_to_internal_ids = new int[maxExternalUserID + 1];
        for(int j=0; j<ratings.getUserIDs().size(); j++){
            external_to_internal_ids[userIds.get(j)] = j;
        }

        computeAverages();
        evaluate(testFile);

    }

}
