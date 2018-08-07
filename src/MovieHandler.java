import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MovieHandler reads the MovieLens data and constructs several mappings:
 *   - userIDs: 
 *       maps the internal user ID to the true user ID 
 *   - movieIDs:
 *       maps the internal movie ID to the true movie ID
 *   - usersToRatings: 
 *       maps the true user ID to a list of movie ratings
 *
 * Constructing these mappings once avoids an indexOf lookup for every
 * movie/user.
 *
 * The internal IDs are introduced to make sure that the IDs nicely go from
 * 0 to num_users or num_movies and such that the same mapping is used for
 * identical inputs.
 *  
 * @author Toon Van Craenendonck
 * @author Pieter Robberechts
 *
 */
public class MovieHandler {

    private String ratingFile;

    private Map<Integer, List<MovieRating>> usersToRatings = new HashMap<Integer, List<MovieRating>>(); 

    private ArrayList<Integer> movieIDs;
    private ArrayList<Integer> userIDs;

    private Map<Integer, Double> movieAverageRatings;

    static double DEFAULT_RATING = 2.5;

    /**
     * Create a new MovieHandler. 
     *
     * Creating a MovieHandler object results in reading the rating and movie
     * files, and constructing the structures described above.
     * 
     * @param fileName name of file containing the ratings
     */
    public MovieHandler(String fileName) {
        this.ratingFile = fileName;

        long startTime = System.currentTimeMillis();
        System.out.println("Reading data.. ");
        this.readData();
        System.out.println("done, took " +  (System.currentTimeMillis() - startTime)/1000.0 + "seconds.");
        System.out.println("--------------");
    }

    /**
     * Returns internal ID to true ID mapping.
     *
     * @return the mapping
     */
    public ArrayList<Integer> getUserIDs() {
        return userIDs;
    }

    /**
     * Reads the MovieLens data into a map, mapping user IDs to lists of movie
     * ratings and creates internal to true ID mappings for users and movies.
     */
    private void readData() {
        Set<Integer> movieSet = new HashSet<Integer>();

        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(ratingFile));
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("::|\t");

                int userID = Integer.parseInt(tokens[0]);
                int movieID = Integer.parseInt(tokens[1]);
                double rating = Double.parseDouble(tokens[2]);

                movieSet.add(movieID);
                if (!usersToRatings.containsKey(userID)) {
                    List<MovieRating> ratingList = new ArrayList<MovieRating>();
                    ratingList.add(new MovieRating(movieID, rating));
                    usersToRatings.put(userID, ratingList);
                } else {
                    usersToRatings.get(userID).add(new MovieRating(movieID, rating));
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // store the user ids as a sorted list (just to make sure that we have a unique ordering)
        userIDs = new ArrayList<Integer>(usersToRatings.keySet());
        Collections.sort(userIDs);

        // same for movie ids
        movieIDs = new ArrayList<Integer>(movieSet);
        Collections.sort(movieIDs);

        // precompute average ratings for each movie
        computeMovieAverageRatings();

    }

    /** 
     * Fetch the average movie rating from the cache.
     *  b @see computeMovieAverageRatings()
     */ 
    public double getMovieAverageRating(Integer movieID) {
        if (movieAverageRatings.containsKey(movieID)) {
            return movieAverageRatings.get(movieID);
        }
        return DEFAULT_RATING;
    }

    /**
     * Returns the user to movie mapping. External IDs are used here.
     * @return the mappings
     */
    public Map<Integer, List<MovieRating>> getUsersToRatings() {
        return usersToRatings;
    }
    
    /**
     * Returns the number of users that were read.
     * @return the number of users
     */
    public int getNumUsers() {
        return userIDs.size();
    }

    /**
     * Returns the number of movies that were read.
     * @return the number of movies
     */
    public int getNumMovies() {
        return movieIDs.size();
    }

    /**
     * Computes and caches movies average ratings. 
     */
    private void computeMovieAverageRatings() {
        movieAverageRatings = new HashMap<Integer, Double>(); 

        Map<Integer, Double> ratingSum = new HashMap<Integer, Double>();
        Map<Integer, Integer> ratingCount = new HashMap<Integer, Integer>();

        for(Integer userID : userIDs) {
            for (MovieRating r : usersToRatings.get(userID)) {
                if (!ratingSum.containsKey(r.getMovieID())) {
                    ratingSum.put(r.getMovieID(), r.getRating()); 
                    ratingCount.put(r.getMovieID(), 1); 
                } else {
                    ratingSum.put(r.getMovieID(), (ratingSum.get(r.getMovieID()) + r.getRating())); 
                    ratingCount.put(r.getMovieID(), ratingCount.get(r.getMovieID()) + 1);
                }
            }
        }

        for (Map.Entry<Integer, Double> entry : ratingSum.entrySet()) {
            int movieID = entry.getKey(); 
            double avg = entry.getValue() / ratingCount.get(movieID);
            movieAverageRatings.put(movieID, avg);
        }
    }

    ArrayList<Integer> getMovieIDs() {
        return movieIDs;
    }


}
