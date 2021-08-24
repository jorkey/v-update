import React, {useState} from 'react';
import clsx from 'clsx';
import { makeStyles } from '@material-ui/styles';
import {
  Card,
  CardHeader,
  CardContent,
  Button,
  Divider, Box,
} from '@material-ui/core';
import AddIcon from '@material-ui/icons/Add';
import GridTable from "../../../../common/components/gridTable/GridTable";
import {
  useAddProviderMutation, useChangeProviderMutation,
  useProvidersInfoQuery, useRemoveProviderMutation,
} from "../../../../generated/graphql";
import ConfirmDialog from "../../../../common/ConfirmDialog";
import DeleteIcon from "@material-ui/icons/Delete";
import Alert from "@material-ui/lab/Alert";
import {GridTableColumnParams, GridTableColumnValue} from "../../../../common/components/gridTable/GridTableColumn";

const useStyles = makeStyles((theme:any) => ({
  root: {},
  content: {
    padding: 0
  },
  inner: {
    minWidth: 800
  },
  ProvidersTable: {
    marginTop: 20
  },
  distributionColumn: {
    padding: '4px',
    paddingLeft: '16px',
    width: '150px'
  },
  urlColumn: {
    padding: '4px',
    paddingLeft: '16px',
  },
  accessTokenColumn: {
    padding: '4px',
    paddingLeft: '16px',
    width: '100px'
  },
  testConsumerColumn: {
    padding: '4px',
    paddingLeft: '16px',
    width: '150px'
  },
  actionsColumn: {
    padding: '4px',
    paddingRight: '40px',
    textAlign: 'right',
    width: '200px',
  },
  uploadStateInterval: {
    padding: '4px',
    paddingLeft: '16px',
    width: '200px'
  },
  controls: {
    display: 'flex',
    justifyContent: 'flex-end'
  },
  control: {
    marginLeft: '50px',
    textTransform: 'none'
  },
  alert: {
    marginTop: 25
  }
}));

const ProvidersManager = () => {
  const classes = useStyles()

  const [error, setError] = useState<string>()

  const { data: providers, refetch: refetchProviders } = useProvidersInfoQuery({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Query providers info error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ addProvider ] = useAddProviderMutation({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Add provider error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ changeProvider ] = useChangeProviderMutation({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Change provider error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ removeProvider ] = useRemoveProviderMutation({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Remove provider error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ addNewRow, setAddNewRow ] = useState(false)
  const [ deleteConfirm, setDeleteConfirm ] = useState<string>()

  const columns: Array<GridTableColumnParams> = [
    {
      name: 'distribution',
      headerName: 'Distribution',
      className: classes.distributionColumn,
      validate: (value, rowNum) => {
        return !!value &&
          !rows.find((row, index) => {
            return index != rowNum && row.get('distribution') == value
          })
      }
    },
    {
      name: 'url',
      headerName: 'URL',
      className: classes.urlColumn,
      editable: true,
      validate: (value) => {
        try {
          if (!value) {
            return false
          }
          new URL(value as string)
          return true
        } catch {
          return false
        }
      }
    },
    {
      name: 'accessToken',
      headerName: 'Access Token',
      className: classes.accessTokenColumn,
      editable: true
    },
    {
      name: 'testConsumer',
      headerName: 'Test Consumer',
      className: classes.testConsumerColumn,
      editable: true
    },
    {
      name: 'uploadStateInterval',
      headerName: 'Upload State Interval (sec)',
      className: classes.uploadStateInterval,
      type: 'number',
      editable: true,
      validate: (value) => {
        try {
          if (!value) {
            return false
          }
          return !isNaN(value as number)
        } catch {
          return false
        }
      }
    },
    {
      name: 'actions',
      headerName: 'Actions',
      type: 'elements',
      className: classes.actionsColumn
    }
  ]

  const rows = new Array<Map<string, GridTableColumnValue>>()
  providers?.providersInfo.forEach(provider => { rows.push(new Map<string, GridTableColumnValue>([
    ['distribution', provider.distribution],
    ['url', provider.url],
    ['accessToken', provider.accessToken],
    ['testConsumer', provider.testConsumer?provider.testConsumer:''],
    ['uploadStateInterval', provider.uploadStateIntervalSec?provider.uploadStateIntervalSec.toString():''],
    ['actions', [<Button key='0' onClick={ () => setDeleteConfirm(provider.distribution) }>
      <DeleteIcon/>
    </Button>]]
  ])) })

  return (
    <Card
      className={clsx(classes.root)}
    >
      <CardHeader
        action={
          <Box
            className={classes.controls}
          >
            <Button
              color="primary"
              variant="contained"
              className={classes.control}
              startIcon={<AddIcon/>}
              onClick={() => setAddNewRow(true) }
            >
              Add New Provider
            </Button>
          </Box>
        }
        title={'Providers'}
      />
      <Divider/>
      <CardContent className={classes.content}>
        <div className={classes.inner}>
          <GridTable
            className={classes.ProvidersTable}
            columns={columns}
            rows={rows}
            addNewRow={addNewRow}
            onRowAddCancelled={ () => setAddNewRow(false) }
            onRowAdded={(row) => {
              setAddNewRow(false)
              addProvider({ variables: {
                distribution: String(row.get('distribution')!),
                url: String(row.get('url')!),
                accessToken: String(row.get('accessToken')!),
                testConsumer: row.get('testConsumer')?String(row.get('testConsumer')!):undefined,
                uploadStateIntervalSec: Number(row.get('uploadStateInterval')!)
              }}).then(() => refetchProviders())
            }}
            onRowChanged={(row, values, oldValues) => {
              setAddNewRow(false)
              return changeProvider({ variables: {
                  distribution: String(values.get('distribution')!),
                  url: String(values.get('url')!),
                  accessToken: String(values.get('accessToken')!),
                  uploadStateIntervalSec: Number(values.get('uploadStateInterval')!)
                }}).then(() => refetchProviders().then(() => {}))}}
          />
          { deleteConfirm ? (
            <ConfirmDialog
              message={`Do you want to delete Provider '${deleteConfirm}'?`}
              open={true}
              close={() => { setDeleteConfirm(undefined) }}
              onConfirm={() => removeProvider({ variables: {
                  distribution: deleteConfirm }}).then(() => refetchProviders()) }
            />) : null }
          {error && <Alert className={classes.alert} severity="error">{error}</Alert>}
        </div>
      </CardContent>
    </Card>
  );
}

export default ProvidersManager