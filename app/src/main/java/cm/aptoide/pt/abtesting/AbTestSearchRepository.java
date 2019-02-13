package cm.aptoide.pt.abtesting;

import java.util.HashMap;
import rx.Observable;
import rx.schedulers.Schedulers;

public class AbTestSearchRepository implements AbTestRepository {
  private SearchAbTestService searchAbTestService;
  private ABTestService service;
  private RealmExperimentPersistence persistence;
  private HashMap<String, ExperimentModel> localCache;
  private String experimentId;

  public AbTestSearchRepository(ABTestService service, HashMap<String, ExperimentModel> localCache,
      RealmExperimentPersistence persistence, SearchAbTestService searchAbTestService) {
    this.searchAbTestService = searchAbTestService;
    this.service = service;
    this.persistence = persistence;
    this.localCache = localCache;
  }

  public Observable<Experiment> getExperiment(String identifier) {
    return getExperimentId(identifier).flatMap(this::resolveExperiment);
  }

  @Override public Observable<Boolean> recordImpression(String identifier) {
    return getExperimentId(identifier).flatMap(id -> {
      if (localCache.containsKey(id) && !localCache.get(id)
          .hasError() && !localCache.get(id)
          .getExperiment()
          .isExperimentOver()) {
        return service.recordImpression(id);
      }
      return Observable.just(false);
    });
  }

  @Override public Observable<Boolean> recordAction(String identifier) {
    return getExperimentId(identifier).flatMap(id -> {
      if (localCache.containsKey(id) && !localCache.get(id)
          .hasError() && !localCache.get(id)
          .getExperiment()
          .isExperimentOver()) {
        return getExperiment(identifier).flatMap(
            experiment -> service.recordAction(id, experiment.getAssignment()));
      }
      return Observable.just(false);
    });
  }

  @Override
  public Observable<Void> cacheExperiment(ExperimentModel experiment, String experimentName) {
    if (localCache.containsKey(experimentName)) localCache.remove(experimentName);

    localCache.put(experimentName, experiment);
    persistence.save(experimentName, experiment.getExperiment());
    return Observable.just(null);
  }

  @Override public Observable<String> getExperimentId(String id) {
    if (id.equals("search")) {
      if (experimentId != null) {
        return Observable.just(experimentId);
      } else {
        return searchAbTestService.getExperimentForSearchAbTest()
            .map(result -> {
              experimentId = result;
              return result;
            });
      }
    }
    return Observable.just(id);
  }

  private Observable<Experiment> resolveExperiment(String experimentId) {
    if (localCache.containsKey(experimentId)) {
      if (!localCache.get(experimentId)
          .getExperiment()
          .isExpired() && !localCache.get(experimentId)
          .hasError() && !localCache.get(experimentId)
          .getExperiment()
          .isExperimentOver()) {
        return Observable.just(localCache.get(experimentId)
            .getExperiment());
      } else {
        return searchAbTestService.getExperimentForSearchAbTest()
            .flatMap(id -> {
              this.experimentId = id;
              return service.getExperiment(id)
                  .flatMap(experimentToCache -> cacheExperiment(experimentToCache, id).flatMap(
                      __ -> Observable.just(experimentToCache.getExperiment())));
            });
      }
    }
    return persistence.get(experimentId)
        .observeOn(Schedulers.io())
        .flatMap(model -> {
          if (!model.hasError() && !model.getExperiment()
              .isExpired()) {
            if (!localCache.containsKey(experimentId)) {
              localCache.put(experimentId, model);
            }
            return Observable.just(model.getExperiment());
          } else {
            return searchAbTestService.getExperimentForSearchAbTest()
                .flatMap(id -> {
                  this.experimentId = id;
                  return service.getExperiment(id)
                      .flatMap(experimentToCache -> cacheExperiment(experimentToCache, id).flatMap(
                          __ -> Observable.just(experimentToCache.getExperiment())));
                });
          }
        });
  }
}
